public class Audits {
    private Donations donation;
    private Distribution distribution;

    public Audits(Donations donation, Distribution distribution) {
        this.donation = donation;
        this.distribution = distribution;
    }

    public Donations getDonation() {
        return donation;
    }

    public void setDonation(Donations donation) {
        this.donation = donation;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
    }
}
