package com.example.legacybtc;

/**
 * I specialize User for the designated recipient of a funded inheritance output. My overridden
 * canRecover accepts only an AVAILABLE plan with the matching beneficiary public key. Owner
 * management is denied. The service also requires the wallet private key and verified funding
 * before signing, so public identity data alone cannot authorize a claim.
 */
public final class Beneficiary extends User {
    public Beneficiary(String name, String publicKey, String address) { super(name, publicKey, address); }
    /**
     * I return a public description of this user's actual role for the status screen. Calling
     * through a User reference executes the Owner or Beneficiary override at runtime, so the
     * interface and permissions use the same role object.
     * @return the actual user role description selected by dynamic dispatch
     */
    @Override public String describeRole() { return "Beneficiary • recover after the Bitcoin timelock"; }
    /**
     * I implement the role's beneficiary-recovery decision. Beneficiary requires the AVAILABLE
     * verified status and its matching plan public key; Owner denies beneficiary recovery. The
     * returned boolean controls UI availability and service permission, with private-key
     * signing and chain checks still required to submit.
     * @param plan InheritancePlan supplied by the calling workflow as described above
     * @param status InheritancePlan.Status supplied by the calling workflow as described above
     * @return whether this role is the matching beneficiary of an AVAILABLE plan
     */
    @Override public boolean canRecover(InheritancePlan plan, InheritancePlan.Status status) {
        return status == InheritancePlan.Status.AVAILABLE && getPublicKey().equals(plan.getBeneficiary().getPublicKey());
    }
    /**
     * I implement the role's plan-management decision through the abstract User contract.
     * Owner compares its public key with the plan owner; Beneficiary denies management.
     * Services call this through User references, demonstrating dynamic binding. This boolean
     * is a role check, while actual spending additionally requires the private key and
     * verified output.
     * @param plan InheritancePlan supplied by the calling workflow as described above
     * @return whether this public role matches the plan owner permission
     */
    @Override public boolean canManage(InheritancePlan plan) { return false; }
}
