package com.example.legacybtc;

/** Prevents fixture tests from running inside the user's normal wallet installation. */
public final class IsolatedWalletTestRunner extends androidx.test.runner.AndroidJUnitRunner {
    @Override public void onStart() {
        if (!getTargetContext().getPackageName().equals("com.example.legacybtc.verification") && !getTargetContext().getPackageName().equals("com.example.legacybtc.verification.regtest"))
            throw new IllegalStateException("Wallet tests require -PisolatedWalletTests=true; normal application data must not be used.");
        super.onStart();
    }
}
