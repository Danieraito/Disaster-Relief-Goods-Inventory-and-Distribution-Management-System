import LIB.*;
import java.time.LocalDate;
import java.util.*;

public class BackendSmokeTest {
    static void p(String s) { System.out.println(s); }
    public static void main(String[] args) throws Exception {
        SchemaUpdater.ensureUpToDate();
        Barangay b = new BarangayRepository().create("Maligaya", "San Isidro", "Laguna", null);
        UserAccountController uc = new UserAccountController();
        UserRepository ur = new UserRepository();
        AdminAccount admin = new AdminAccount("adm-1", "Andres Villanueva", "andres", "a@x.example", "Admin#12345");
        ur.create(admin, null);
        String t1 = uc.invite("Maria Dela Cruz", "m@x.example", UserAccount.Role.BARANGAY_OFFICIAL, b, admin);
        String t2 = uc.invite("Leo Garcia", "l@x.example", UserAccount.Role.ENCODER, b, admin);
        UserAccount maria = ur.authenticate("m@x.example", t1).orElseThrow();
        UserAccount leo = ur.authenticate("l@x.example", t2).orElseThrow();

        InventoryController ic = new InventoryController();
        Inventory food = ic.addItem("Family food pack", "Food", 0, "pack", 100, "Hall A-01", null, "", b, maria);
        Inventory water = ic.addItem("Drinking water", "Water", 0, "liter", 200, "Hall A-02", null, "", b, maria);
        Inventory kit = ic.addItem("Hygiene kit", "Hygiene kits", 90, "kit", 100, "Hall B-01", null, "", b, maria);

        DonationController dc = new DonationController();
        Donor d = dc.saveDonor(null, "Bayanihan Foundation", Donor.Type.ORGANIZATION, "Carla Navarro", "0917 000 0102", "bayanihan@example.org", b, leo);
        p("donor " + d.getDonorId());
        List<Inventory> inv = ic.list(b, false, maria);
        List<DonationRepository.Line> lines = new ArrayList<>();
        lines.add(new DonationRepository.Line(food.getItemId(), 480, Time.today().plusDays(27)));
        lines.add(new DonationRepository.Line(water.getItemId(), 960, Time.today().plusDays(200)));
        lines.add(new DonationRepository.Line(kit.getItemId(), 40, null));
        // encoder cannot post
        try { dc.record(null, d, b, Time.today(), "Barangay hall", "BF 1003", "Carla", "", lines, inv, DonationRepository.Mode.POST, leo); }
        catch (SecurityException e) { p("encoder POST blocked: " + e.getMessage()); }
        Donations pending = dc.record(null, d, b, Time.today(), "Barangay hall", "BF 1003", "Carla", "", lines, inv, DonationRepository.Mode.SUBMIT, leo);
        p("pending " + pending.getDonationId() + " " + pending.getStatus());
        Donations posted = dc.approve(pending.getDonationId(), maria);
        p("posted " + posted.getStatus() + " by " + posted.getReceivedBy().getName());
        for (DonationRepository.Movement m : dc.movements(dc.find(posted.getDonationId(), maria)))
            p("  movement " + m.itemName + " " + m.before + " + " + m.added + " = " + m.after);
        Donations direct = dc.record(null, d, b, Time.today(), "Barangay hall", "BF 1004", "", "", List.of(new DonationRepository.Line(kit.getItemId(), 10, null)), inv, DonationRepository.Mode.POST, maria);
        p("direct post " + direct.getDonationId() + " " + direct.getStatus());
        for (DonationRepository.Row r : dc.list(b, new DonationRepository.Filter(), maria)) p("  donation row " + r.donationId + " " + r.status + " " + r.goods + " by " + r.officer);
        for (DonorRepository.Row r : dc.donorRows(b)) p("  donor row " + r.donor.getName() + " latest " + r.latestDonationId + " " + r.latestGoods);

        DisasterController disc = new DisasterController();
        Disaster typhoon = disc.addDisaster("Typhoon Amihan", "Typhoon", Time.today().minusDays(10), "High", "Purok 1, Purok 2", "Riverside", "Flooding", "Ana Mendoza", "Maligaya Elementary School", b, maria);
        p("disaster " + typhoon.getDisasterId());

        BeneficiaryController bc = new BeneficiaryController();
        List<FamilyMember> members = new ArrayList<>();
        members.add(new FamilyMember("t1", "Roberto Reyes", 34, null, "Male", false, false, false, false));
        members.add(new FamilyMember("t2", "Elena Reyes", 31, null, "Female", false, false, true, false));
        members.add(new FamilyMember("t3", "Rosa Reyes", 67, null, "Female", true, false, false, false));
        members.add(new FamilyMember("t4", "Mateo Reyes", 0, 6, "Male", false, true, false, false));
        Map<String,Integer> needs = new LinkedHashMap<>(); needs.put("Food", 2); needs.put("Water", 12); needs.put("Hygiene kits", 1);
        Beneficiaries hh = bc.registerHousehold(typhoon, "Roberto Reyes", "0917 000 0042", "18 Mabini Street", false, "Purok 2", 4, members, Beneficiaries.DAMAGE_PARTIAL, "Evacuated", "Maligaya Elementary School", "Roof damage", needs, leo);
        p("household " + hh.getBeneficiaryId() + " head " + hh.getHouseholdHead().getFullName() + " members " + hh.getHouseholdSize());
        try { bc.registerHousehold(typhoon, "Roberto Reyes", "0917", "18 Mabini Street", false, "Purok 2", 4, members, Beneficiaries.DAMAGE_PARTIAL, "Evacuated", "", "", needs, leo); }
        catch (IllegalArgumentException e) { p("duplicate household blocked: " + e.getMessage()); }
        List<FamilyMember> m2 = new ArrayList<>(); m2.add(new FamilyMember("x1", "Jose Santos", 45, null, "Male", false, false, false, true));
        Beneficiaries santos = bc.registerHousehold(typhoon, "Jose Santos", "0917 000 0038", "6 Rizal St", false, "Purok 1", 1, m2, Beneficiaries.DAMAGE_TOTAL, "Evacuated", "", "", needs, leo);

        EvacuationCenterController ecc = new EvacuationCenterController();
        EvacuationCenter school = new EvacuationCenterRepository().create("Maligaya Elementary School", "Rizal St", 300, b, maria);

        DistributionController dist = new DistributionController();
        List<Inventory> inv2 = ic.list(b, false, maria);
        Map<String, Inventory> byName = new HashMap<>(); for (Inventory i : inv2) byName.put(i.getItemName(), i);
        DistributionRepository.Request req = new DistributionRepository.Request();
        req.disaster = typhoon; req.householdId = santos.getBeneficiaryId(); req.date = Time.today();
        req.acknowledgment = "Signed claim stub";
        Inventory f2 = byName.get("Family food pack"), w2 = byName.get("Drinking water"), k2 = byName.get("Hygiene kit");
        req.lines.add(new DistributionRepository.Line(f2.getItemId(), f2.getBatches().get(0).getBatchId(), 2));
        req.lines.add(new DistributionRepository.Line(w2.getItemId(), w2.getBatches().get(0).getBatchId(), 12));
        String kb = null; for (InventoryBatch ib : k2.getBatches()) if (ib.getRemainingQuantity() > 0) { kb = ib.getBatchId(); break; }
        req.lines.add(new DistributionRepository.Line(k2.getItemId(), kb, 1));
        p("check household release: " + dist.check(req, maria));
        Distribution d1 = dist.record(req, DistributionRepository.Mode.POST, maria);
        p("released " + d1.getDistributionId() + " " + d1.getStatus());
        DistributionRepository.Problem dup = dist.check(req, maria);
        p("duplicate: " + (dup == null ? "NONE (BUG)" : dup.kind + " | " + dup.message));
        p("VAL ref " + dist.logBlocked(dup, req, maria));

        DistributionRepository.Request big = new DistributionRepository.Request();
        big.disaster = typhoon; big.centerId = school.getCenterId(); big.householdId = hh.getBeneficiaryId(); big.date = Time.today();
        big.lines.add(new DistributionRepository.Line(w2.getItemId(), w2.getBatches().get(0).getBatchId(), 1300));
        DistributionRepository.Problem stock = dist.check(big, maria);
        p("stock: " + stock.kind + " | " + stock.message);
        big.lines.clear(); big.lines.add(new DistributionRepository.Line(w2.getItemId(), w2.getBatches().get(0).getBatchId(), 12));
        Distribution pendingRel = dist.record(big, DistributionRepository.Mode.SUBMIT, leo);
        p("encoder submitted " + pendingRel.getDistributionId() + " " + pendingRel.getStatus());
        Distribution approved = dist.approve(pendingRel.getDistributionId(), maria);
        p("approved " + approved.getStatus() + " receipt " + approved.getReceiptReference());
        for (DistributionRepository.Movement m : dist.movements(dist.find(approved.getDistributionId(), maria)))
            p("  rcpt " + m.itemName + " " + m.quantity + " " + m.batchId + " " + m.before + "->" + m.after);
        for (DistributionRepository.Row r : dist.list(b, new DistributionRepository.Filter(), maria)) p("  row " + r.distributionId + " " + r.status + " -> " + r.recipient() + " " + r.goods);

        DisasterStats st = disc.stats(typhoon);
        p("stats registered=" + st.registered + " individuals=" + st.individuals + " assisted=" + st.assisted + " vulnerable=" + st.vulnerablePeople
            + " seniors=" + st.seniors + " pwd=" + st.pwd + " preg=" + st.pregnant + " inf=" + st.infants + " areas=" + st.areas.size() + " needs=" + st.pendingNeeds);
        BeneficiaryRepository.Page pg = bc.page(typhoon, new BeneficiaryRepository.Filter(), 1, 10, maria);
        for (BeneficiaryRepository.Row r : pg.rows) p("  hh " + r.householdId + " " + r.head + " assisted=" + r.assisted + " needs=" + r.needCategories);
        Beneficiaries loaded = bc.find(santos.getBeneficiaryId(), typhoon, maria);
        p("santos released " + loaded.getReleased() + " needs " + loaded.getNeeds() + " assisted " + loaded.isAssisted());
        bc.updateHousehold(hh, "0917 111 2222", Beneficiaries.DAMAGE_PARTIAL, "At home", "Verified during follow-up", leo);
        AuditRepository.Filter f = new AuditRepository.Filter(); f.barangayId = b.getBarangayId(); f.limit = 40;
        for (Audits a : new AuditRepository().find(f)) p("  AUDIT " + a.getReference() + " | " + a.getAction() + " | " + a.getSummary() + " | " + a.getMovement() + " | " + a.getActorName());
        p("ALL OK");
    }
}
