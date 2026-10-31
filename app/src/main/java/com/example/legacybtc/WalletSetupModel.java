package com.example.legacybtc;

import androidx.lifecycle.ViewModel;
import java.util.concurrent.CompletableFuture;

/** Retains only an operation result/filename across rotation, never recovery text. */
public final class WalletSetupModel extends ViewModel {
    CompletableFuture<String> operation;
}
