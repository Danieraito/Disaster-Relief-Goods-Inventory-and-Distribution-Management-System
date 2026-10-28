package SERVER;

import LIB.AuditRepository;
import LIB.Barangay;
import LIB.Database;
import LIB.DonationController;
import LIB.DonationRepository;
import LIB.Donations;
import LIB.Donor;
import LIB.DonorRepository;
import LIB.Inventory;
import LIB.InventoryRepository;
import LIB.TransactionItem;
import LIB.UserAccount;
import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Donations screen. A donation is entered, submitted (PENDING) and then approved or rejected.
 * Only approval adds stock: each line becomes a new inventory batch. Officials and Admins can
 * post straight away; Encoders' donations wait for an Official.
 */
final class DonationApi {
    private final DonationRepository repository = new DonationRepository();
    private final DonorRepository donors = new DonorRepository();
    private final DonationController controller = new DonationController();
    private final AuditRepository audits = new AuditRepository();

    void list(HttpExchange exchange, UserAccount user) throws Exception {
        Http.sendJson(exchange, 200, Json.obj(
                "donations", repository.listSummaries(Api.scope(user)),
                "donors", donors.listSummaries()));
    }

    void record(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String donorId = Api.required(form, "donorId", "Donor");
        String date = Api.date(Api.text(form, "date"), "Date received", false);
        String location = Api.text(form, "receivingLocation");
        String reference = Api.text(form, "deliveryReference");
        boolean postNow = "1".equals(Api.text(form, "post"));
        Barangay barangay = Api.targetBarangay(user, Api.text(form, "barangayId"));

        List<String> itemIds = Api.all(form, "itemId");
        List<String> quantities = Api.all(form, "quantity");
        List<String> expiries = Api.all(form, "expiry");
        if (itemIds.isEmpty()) {
            throw new ApiError(400, "Add at least one item line.");
        }
        if (quantities.size() != itemIds.size() || expiries.size() != itemIds.size()) {
            throw new ApiError(400, "Item lines are incomplete. Please check every line.");
        }
        LocalDate today = Database.today();

        Map<String, Object> result = Api.inTransaction((Connection connection) -> {
            Donor donor = DonorRepository.findById(connection, donorId);
            if (donor == null) {
                throw new ApiError(400, "Choose a donor, or add one with Manage donors.");
            }
            String donationId = Database.nextId(connection, "donations", "donation_id", "DON");
            Donations donation = controller.createDonation(donationId, donor, date, location,
                    reference.isEmpty() ? null : reference, user);

            // Lines for the same item must share one Inventory object (the model compares them).
            Map<String, Inventory> loaded = new HashMap<>();
            for (int i = 0; i < itemIds.size(); i++) {
                String itemId = itemIds.get(i);
                if (!loaded.containsKey(itemId)) {
                    loaded.put(itemId, InventoryRepository.findById(connection, itemId));
                }
                Inventory item = loaded.get(itemId);
                if (item == null) {
                    throw new ApiError(400, "Choose an item for line " + (i + 1) + ".");
                }
                if (!item.getBarangay().getBarangayId().equals(barangay.getBarangayId())) {
                    throw new ApiError(400, item.getItemName() + " belongs to another barangay's inventory.");
                }
                int quantity = Api.wholeNumber(quantities.get(i), "Quantity on line " + (i + 1));
                String expiry = Api.date(expiries.get(i), "Expiry date on line " + (i + 1), true);
                if (expiry != null && LocalDate.parse(expiry).isBefore(today)) {
                    throw new ApiError(400, item.getItemName() + " has already expired (" + expiry
                            + "). Expired goods cannot be received into stock.");
                }
                controller.addDonationItem(donation, new TransactionItem(item, quantity, null, expiry), user);
            }

            controller.submitDonation(donation, user);
            DonationRepository.insert(connection, donation, barangay.getBarangayId(), user);
            audits.record(connection, user, "Donations", barangay.getBarangayId(), "Donation submitted",
                    donationId, null, Api.lineSummary(donation.getItems()), donor.getName()
                            + (reference.isEmpty() ? "" : " · " + reference), donation, null);

            boolean posted = false;
            if (postNow && user.canManageRecords()) {
                post(connection, donation, user, null);
                posted = true;
            }
            return Json.obj("ok", true, "id", donationId, "posted", posted);
        });
        Http.sendJson(exchange, 201, result);
    }

    /** Approve (post to stock) or reject a pending donation. */
    void decide(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String donationId = Api.required(form, "id", "Donation");
        boolean approve = "approve".equals(Api.text(form, "decision"));
        String reason = Api.text(form, "reason");
        if (!approve && reason.isEmpty()) {
            throw new ApiError(400, "Give a reason for rejecting this donation.");
        }

        Api.inTransaction((Connection connection) -> {
            Api.requireAccess(user, DonationRepository.barangayOf(connection, donationId));
            Donations donation = DonationRepository.load(connection, donationId);
            if (approve) {
                post(connection, donation, user, reason.isEmpty() ? null : reason);
            } else {
                controller.rejectDonation(donation, user);
                DonationRepository.saveDecision(connection, donation);
                audits.record(connection, user, "Donations", donation.getBarangay().getBarangayId(),
                        "Donation rejected", donationId, "Pending", "Rejected · no stock movement", reason,
                        donation, null);
            }
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    /** Approves the donation through the model, saves the new batches and writes the audit row. */
    private void post(Connection connection, Donations donation, UserAccount user, String reason) throws Exception {
        Map<Inventory, Integer> before = Api.stockBefore(donation.getItems());
        controller.approveDonation(donation, user);
        DonationRepository.saveDecision(connection, donation);
        String[] change = Api.stockChange(before);
        audits.record(connection, user, "Donations", donation.getBarangay().getBarangayId(), "Donation posted",
                donation.getDonationId(), change[0], change[1],
                reason == null ? donation.getDonor().getName() : reason, donation, null);
    }

    void createDonor(HttpExchange exchange, UserAccount user) throws Exception {
        if (!user.canEnterRecords()) {
            throw new ApiError(403, "Your role cannot add donors.");
        }
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String name = Api.required(form, "name", "Donor name");
        Donor.Type type;
        try {
            type = Donor.Type.valueOf(Api.text(form, "type"));
        } catch (IllegalArgumentException e) {
            throw new ApiError(400, "Choose a donor type.");
        }
        String contactPerson = Api.text(form, "contactPerson");
        String contactDetails = Api.text(form, "contactDetails");

        String id = Api.inTransaction((Connection connection) -> {
            String donorId = Database.nextId(connection, "donors", "donor_id", "DNR");
            DonorRepository.save(connection, new Donor(donorId, name, type, contactPerson, contactDetails));
            audits.record(connection, user, "Donations", Api.scope(user), "Donor added", donorId, null,
                    name + " · " + type, null, null, null);
            return donorId;
        });
        Http.sendJson(exchange, 201, Json.obj("ok", true, "id", id));
    }

}
