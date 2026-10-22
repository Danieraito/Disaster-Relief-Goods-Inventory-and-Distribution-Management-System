package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Loads a fictional "illustrative training" workspace that mirrors the BarangayCare design: stock with an
 * expired medicine batch, two active disasters, registered households, confirmed donations and releases,
 * and one donation and one release still waiting for approval. Everything goes through the normal
 * controllers, so the audit log and all business rules behave exactly as they do for real records.
 */
public final class DemoData {
    public static final String PASSWORD = "Maligaya#2026";

    private static final String[] SURNAMES = { "Reyes", "Santos", "Cruz", "Bautista", "Aquino", "Garcia", "Mendoza",
        "Torres", "Ramos", "Navarro", "Lim", "Castillo", "Flores", "Rivera", "Gonzales", "Morales", "Pascual",
        "Domingo", "Manalo", "Salazar", "Valdez", "Soriano", "Hernandez", "Mercado", "Tolentino", "De Leon",
        "Aguilar", "Fernandez", "Villareal", "Magsaysay", "Dizon", "Tan", "Estrada", "Panganiban", "Lacson",
        "Macaraeg", "Abad", "Natividad", "Suarez", "Ocampo" };
    private static final String[] MALE = { "Roberto", "Jose", "Carlo", "Antonio", "Ramon", "Eduardo", "Mario", "Rodel",
        "Noel", "Arnel", "Benjie", "Ferdie" };
    private static final String[] FEMALE = { "Lourdes", "Mila", "Elena", "Rosa", "Liza", "Gloria", "Marites", "Corazon",
        "Teresita", "Nenita", "Jocelyn", "Luzviminda" };
    private static final String[] STREETS = { "Mabini St.", "Rizal St.", "Bonifacio St.", "Luna St.", "Del Pilar St." };

    private DemoData() {
    }

    public static boolean isWorkspaceEmpty(Barangay barangay) throws SQLException {
        return new InventoryRepository().findByBarangay(barangay.getBarangayId(), true).isEmpty()
                && new DisasterRepository().findByBarangay(barangay.getBarangayId()).isEmpty();
    }

    public static String seed(Barangay barangay, UserAccount admin, Consumer<String> progress) throws SQLException {
        if (!isWorkspaceEmpty(barangay)) {
            throw new IllegalStateException("Training data can only be loaded into an empty workspace.");
        }
        UserRepository users = new UserRepository();
        UserAccountController accounts = new UserAccountController();
        progress.accept("Creating demo accounts…");
        UserAccount maria = account(users, accounts, "Maria Dela Cruz", "m.delacruz@maligaya.example",
                UserAccount.Role.BARANGAY_OFFICIAL, barangay, admin);
        UserAccount ana = account(users, accounts, "Ana Mendoza", "a.mendoza@maligaya.example",
                UserAccount.Role.BARANGAY_OFFICIAL, barangay, admin);
        UserAccount leo = account(users, accounts, "Leo Garcia", "l.garcia@maligaya.example",
                UserAccount.Role.ENCODER, barangay, admin);
        UserAccount paolo = account(users, accounts, "Paolo Torres", "p.torres@maligaya.example",
                UserAccount.Role.ENCODER, barangay, admin);
        users.setStatus(paolo, UserAccount.Status.INACTIVE, "End of temporary encoding assignment.", admin);

        InventoryController inventory = new InventoryController();
        DonationController donations = new DonationController();
        DisasterController disasters = new DisasterController();
        BeneficiaryController households = new BeneficiaryController();
        DistributionController releases = new DistributionController();
        LocalDate today = Time.today();
        Random random = new Random(2026);

        // ---- plan: how much each item is consumed, so the final stock matches the design's figures
        int householdReleases = 0;
        final int amihanHouseholds = 24;
        final int amihanAssisted = 17;
        final int floodHouseholds = 6;
        final int floodAssisted = 3;
        final int fireHouseholds = 3;
        final int quakeHouseholds = 4;
        householdReleases = amihanAssisted + floodAssisted + fireHouseholds + quakeHouseholds + 1; // +1 evacuation center
        int foodOut = householdReleases * 2;
        int waterOut = householdReleases * 12;
        int kitsOut = householdReleases + 6; // releases plus six damaged kits

        progress.accept("Adding relief items…");
        Inventory food = inventory.addItem("Family food pack", "Food", 480 - 120 + foodOut, "pack", 100,
                "Hall · A-01", today.plusDays(27).toString(), "Opening count verified against the stock card.", barangay, ana);
        Inventory water = inventory.addItem("Drinking water", "Water", 960 - 240 - 120 + waterOut, "liter", 200,
                "Hall · A-02", today.plusDays(200).toString(), "Opening count verified against the stock card.", barangay, ana);
        Inventory kit = inventory.addItem("Hygiene kit", "Hygiene kits", 84 + kitsOut, "kit", 80,
                "Barangay hall · Shelf B-01", null, "Opening count verified against the stock card.", barangay, ana);
        inventory.addItem("Blanket", "Shelter materials", 120, "piece", 30, "Hall · B-02", null, "Opening count.", barangay, ana);
        Inventory medicine = inventory.addItem("Paracetamol", "Medicine", 28, "box", 50, "Clinic · C-01",
                today.plusDays(300).toString(), "Opening count verified against the clinic stock card.", barangay, ana);
        inventory.addItem("Shelter tarpaulin", "Shelter materials", 0, "piece", 20, "Hall · B-03", null, "", barangay, ana);
        inventory.addItem("Clothing bundle", "Clothing", 160, "pack", 40, "Hall · B-04", null, "Opening count.", barangay, ana);

        progress.accept("Recording donations…");
        Donor bayanihan = donations.saveDonor(null, "Bayanihan Foundation", Donor.Type.ORGANIZATION, "Carla Navarro",
                "0917 000 0102", "bayanihan@example.org", barangay, ana);
        Donor lgu = donations.saveDonor(null, "San Isidro LGU", Donor.Type.AGENCY, "Ana Mendoza", "0917 000 0101",
                "lgu@example.org", barangay, ana);
        Donor youth = donations.saveDonor(null, "Maligaya Youth Council", Donor.Type.ORGANIZATION, "Paolo Torres",
                "0917 000 0106", "youth@example.org", barangay, ana);
        Donor isabel = donations.saveDonor(null, "Isabel Lim", Donor.Type.INDIVIDUAL, "Isabel Lim", "0917 000 0104",
                "isabel@example.org", barangay, ana);
        Donor coop = donations.saveDonor(null, "Maligaya Community Cooperative", Donor.Type.ORGANIZATION, "Nina Ramos",
                "0917 000 0113", "coop@example.org · Purok 3, Barangay Maligaya", barangay, leo);
        List<Inventory> items = inventory.list(barangay, false, maria);
        Map<String, Inventory> byName = byName(items);

        List<DonationRepository.Line> lines = new ArrayList<>();
        lines.add(new DonationRepository.Line(byName.get("Family food pack").getItemId(), 120, today.plusDays(27)));
        lines.add(new DonationRepository.Line(byName.get("Drinking water").getItemId(), 240, today.plusDays(200)));
        donations.record(null, bayanihan, barangay, today.minusDays(14), "Barangay hall · Receiving desk", "BF 1003",
                "Carla Navarro", "Delivery slip counted and verified.", lines, items, DonationRepository.Mode.POST, maria);
        lines = new ArrayList<>();
        lines.add(new DonationRepository.Line(byName.get("Shelter tarpaulin").getItemId(), 60, null));
        donations.record(null, lgu, barangay, today.minusDays(12), "Barangay hall · Receiving desk", "LGU 0412",
                "Ana Mendoza", "", lines, items, DonationRepository.Mode.POST, ana);
        lines = new ArrayList<>();
        lines.add(new DonationRepository.Line(byName.get("Drinking water").getItemId(), 120, today.plusDays(200)));
        donations.record(null, youth, barangay, today.minusDays(11), "Barangay hall · Receiving desk", "MYC 0099",
                "Paolo Torres", "", lines, items, DonationRepository.Mode.POST, ana);
        lines = new ArrayList<>();
        lines.add(new DonationRepository.Line(byName.get("Clothing bundle").getItemId(), 20, null));
        donations.record(null, isabel, barangay, today.minusDays(10), "Barangay hall · Receiving desk", "",
                "Isabel Lim", "", lines, items, DonationRepository.Mode.POST, maria);
        lines = new ArrayList<>();
        lines.add(new DonationRepository.Line(medicine.getItemId(), 8, today.minusDays(3)));
        donations.record(null, lgu, barangay, today.minusDays(60), "Clinic · Receiving shelf", "LGU 0301", "Ana Mendoza",
                "Batch MED-0930. Expired during storage.", lines, items, DonationRepository.Mode.POST, ana);

        progress.accept("Opening disaster events…");
        EvacuationCenter school = new EvacuationCenterRepository().create("Maligaya Elementary School",
                "Rizal Street, Barangay " + barangay.getName(), 300, barangay, maria);
        Disaster fire = disasters.addDisaster("Market fire", "Fire", today.minusDays(60), "Moderate", "Purok 5",
                "Public market, Purok 5", "Fire affecting three market-side homes.", "Ana Mendoza",
                school.getName(), barangay, maria);
        Disaster quake = disasters.addDisaster("July earthquake", "Earthquake", today.minusDays(90), "Moderate",
                "Barangay-wide", "Barangay-wide", "Moderate shaking with minor structural damage.", "Ana Mendoza",
                school.getName(), barangay, maria);
        Disaster amihan = disasters.addDisaster("Typhoon Amihan", "Typhoon", today.minusDays(6), "High",
                "Purok 1, Purok 2, Purok 3", "Puroks 1, 2 and 3, Barangay " + barangay.getName(),
                "Flooding and strong winds damaged homes along the riverside and displaced residents to the school "
                + "evacuation center.", "Ana Mendoza", school.getName(), barangay, maria);
        Disaster flood = disasters.addDisaster("Riverside flood", "Flood", today.minusDays(9), "Moderate", "Purok 4",
                "Riverside, Purok 4", "Flash flooding along the riverbank.", "Ana Mendoza", school.getName(), barangay, maria);

        Map<String, Integer> needs = new LinkedHashMap<>();
        int number = 0;
        progress.accept("Registering households and releasing goods…");
        List<Beneficiaries> fireHomes = register(households, fire, fireHouseholds, 5, random, leo, number += 100, "Purok 5");
        List<Beneficiaries> quakeHomes = register(households, quake, quakeHouseholds, 4, random, leo, number += 100, "Purok 1");
        for (Beneficiaries home : fireHomes) {
            release(releases, inventory, barangay, fire, home, null, today.minusDays(58), maria);
        }
        for (Beneficiaries home : quakeHomes) {
            release(releases, inventory, barangay, quake, home, null, today.minusDays(88), maria);
        }
        disasters.closeDisaster(fire, "All affected households assisted; response completed.", maria);
        disasters.closeDisaster(quake, "All affected households assisted; response completed.", maria);
        List<Beneficiaries> amihanHomes = register(households, amihan, amihanHouseholds, 1, random, leo, number += 100,
                null);
        List<Beneficiaries> floodHomes = register(households, flood, floodHouseholds, 4, random, leo, number += 100, "Purok 4");
        for (int index = 0; index < amihanAssisted; index++) {
            release(releases, inventory, barangay, amihan, amihanHomes.get(index), null, today.minusDays(3), maria);
        }
        for (int index = 0; index < floodAssisted; index++) {
            release(releases, inventory, barangay, flood, floodHomes.get(index), null, today.minusDays(5), maria);
        }
        // one release to the evacuation center, confirmed by an official
        release(releases, inventory, barangay, amihan, amihanHomes.get(amihanAssisted), school, today.minusDays(1), maria);

        progress.accept("Adding stock adjustments and pending approvals…");
        Inventory kitNow = inventory.find(kit.getItemId(), maria);
        inventory.adjustStock(kitNow, firstBatchWithStock(kitNow, today), StockAdjustment.Type.DECREASE,
                StockAdjustment.Reason.DAMAGE, 6, "Torn packaging found during shelf inspection; count verified by Ana Mendoza.",
                today.minusDays(1), maria);
        kitNow = inventory.find(kit.getItemId(), maria);
        inventory.updateItem(kitNow, "Hygiene kit", "Hygiene kits", "kit", 100, "Barangay hall · Shelf B-01",
                "Increase buffer for the next Typhoon Amihan release.", maria);

        items = inventory.list(barangay, false, maria);
        byName = byName(items);
        lines = new ArrayList<>();
        lines.add(new DonationRepository.Line(byName.get("Blanket").getItemId(), 40, null));
        donations.record(null, coop, barangay, today, "Barangay hall · Receiving desk", "MCC 0007", "Nina Ramos",
                "Waiting for an official to confirm.", lines, items, DonationRepository.Mode.SUBMIT, leo);
        DistributionRepository.Request pending = new DistributionRepository.Request();
        pending.disaster = amihan;
        pending.householdId = amihanHomes.get(amihanAssisted + 1).getBeneficiaryId();
        pending.date = today;
        pending.acknowledgment = "Signed claim stub · file on confirmation";
        Inventory foodNow = byName.get("Family food pack");
        pending.lines.add(new DistributionRepository.Line(foodNow.getItemId(), firstBatchWithStock(foodNow, today), 2));
        releases.record(pending, DistributionRepository.Mode.SUBMIT, leo);

        return "Training workspace loaded: 7 items, 5 donors, 4 disaster events, "
                + (amihanHouseholds + floodHouseholds + fireHouseholds + quakeHouseholds) + " households and "
                + householdReleases + " confirmed releases. Demo accounts use the password " + PASSWORD + ".";
    }

    private static UserAccount account(UserRepository users, UserAccountController controller, String name,
            String email, UserAccount.Role role, Barangay barangay, UserAccount admin) throws SQLException {
        UserAccount account = controller.createAccount(java.util.UUID.randomUUID().toString(), name, email, email,
                PASSWORD, role, barangay, admin);
        users.create(account, admin);
        return account;
    }

    private static Map<String, Inventory> byName(List<Inventory> items) {
        Map<String, Inventory> map = new LinkedHashMap<>();
        for (Inventory item : items) {
            map.put(item.getItemName(), item);
        }
        return map;
    }

    private static String firstBatchWithStock(Inventory item, LocalDate asOf) {
        InventoryBatch best = null;
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getRemainingQuantity() > 0 && !batch.isExpired(asOf)) {
                LocalDate expiry = batch.getExpiryLocalDate();
                if (best == null || (expiry != null && (best.getExpiryLocalDate() == null
                        || expiry.isBefore(best.getExpiryLocalDate())))) {
                    best = batch;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("No usable batch of " + item.getItemName() + " has stock.");
        }
        return best.getBatchId();
    }

    private static void release(DistributionController releases, InventoryController inventory, Barangay barangay,
            Disaster disaster, Beneficiaries household, EvacuationCenter center, LocalDate date, UserAccount official)
            throws SQLException {
        Map<String, Inventory> items = byName(inventory.list(barangay, false, official));
        DistributionRepository.Request request = new DistributionRepository.Request();
        request.disaster = disaster;
        request.householdId = household.getBeneficiaryId();
        request.centerId = center == null ? null : center.getCenterId();
        request.date = date;
        request.acknowledgment = center == null ? "Signed claim stub · file on confirmation"
                : "Signed paper center receipt · file on confirmation";
        int[] quantities = { 2, 12, 1 };
        String[] names = { "Family food pack", "Drinking water", "Hygiene kit" };
        for (int index = 0; index < names.length; index++) {
            Inventory item = items.get(names[index]);
            request.lines.add(new DistributionRepository.Line(item.getItemId(), firstBatchWithStock(item, date),
                    quantities[index]));
        }
        releases.record(request, DistributionRepository.Mode.POST, official);
    }

    private static List<Beneficiaries> register(BeneficiaryController households, Disaster disaster, int count,
            int fixedPurok, Random random, UserAccount encoder, int offset, String forcePurok) throws SQLException {
        List<Beneficiaries> registered = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String surname = SURNAMES[(index * 7 + offset) % SURNAMES.length];
            boolean maleHead = random.nextBoolean();
            String headName = (maleHead ? MALE : FEMALE)[random.nextInt(12)] + " " + surname;
            int purokNumber = forcePurok != null ? Integer.parseInt(forcePurok.replace("Purok ", ""))
                    : 1 + index % 3;
            String purok = "Purok " + purokNumber;
            String address = (10 + random.nextInt(60)) + " " + STREETS[random.nextInt(STREETS.length)];
            List<FamilyMember> members = new ArrayList<>();
            members.add(new FamilyMember("m1", headName, 28 + random.nextInt(35), null, maleHead ? "Male" : "Female",
                    false, false, false, random.nextInt(20) == 0));
            int size = 3 + random.nextInt(4);
            for (int position = 2; position <= size; position++) {
                boolean female = random.nextBoolean();
                String first = (female ? FEMALE : MALE)[random.nextInt(12)];
                int kind = random.nextInt(8);
                if (kind == 0) {
                    members.add(new FamilyMember("m" + position, first + " " + surname, 62 + random.nextInt(25), null,
                            female ? "Female" : "Male", true, false, false, false));
                } else if (kind == 1) {
                    members.add(new FamilyMember("m" + position, first + " " + surname, 0, 2 + random.nextInt(10), "Male",
                            false, true, false, false));
                } else if (kind == 2 && female) {
                    members.add(new FamilyMember("m" + position, first + " " + surname, 22 + random.nextInt(14), null,
                            "Female", false, false, true, false));
                } else {
                    members.add(new FamilyMember("m" + position, first + " " + surname, 4 + random.nextInt(30), null,
                            female ? "Female" : "Male", false, false, false, random.nextInt(25) == 0));
                }
            }
            boolean total = random.nextInt(10) < 3;
            Map<String, Integer> needs = new LinkedHashMap<>();
            needs.put("Food", 2);
            needs.put("Water", 12);
            needs.put("Hygiene kits", 1);
            if (total) {
                needs.put("Shelter materials", 1);
            }
            boolean evacuated = random.nextInt(10) < 6;
            registered.add(households.registerHousehold(disaster, headName, "0917 000 " + String.format("%04d",
                    offset + index), address + ", Barangay " + disaster.getBarangay().getName(), false, purok,
                    members.size(), members, total ? Beneficiaries.DAMAGE_TOTAL : Beneficiaries.DAMAGE_PARTIAL,
                    evacuated ? "Evacuated" : "At home", evacuated ? disaster.getEvacuationSite() : "",
                    total ? "Roof and walls damaged." : "Roof damage.", needs, encoder));
        }
        return registered;
    }
}
