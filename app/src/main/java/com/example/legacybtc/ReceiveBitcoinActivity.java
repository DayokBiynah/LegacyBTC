package com.example.legacybtc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

public class ReceiveBitcoinActivity extends SelectedWalletActivity {
    private String displayedAddress = "", displayedPublicKey = "", renderingAddress = "";
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_receive);
        findViewById(R.id.copyAddressButton).setOnClickListener(view -> {
            if (displayedAddress.isEmpty()) return;
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(WalletPolicy.NETWORK_LABEL + " address", displayedAddress));
            showMessage(getString(R.string.address_copied));
        });
        findViewById(R.id.generateAddressButton).setOnClickListener(view -> {
            if (walletReady) work(service::generateAddress, address -> refresh());
        });
        findViewById(R.id.copyBeneficiaryButton).setOnClickListener(view -> {
            if (displayedAddress.isEmpty()) return;
            String info = WalletPolicy.NETWORK_LABEL + " SegWit address: " + displayedAddress + "\nCompressed public key: " + displayedPublicKey;
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Beneficiary information",info));
            showMessage("Public beneficiary information copied. Keep your Recovery Phrase private.");
        });
        findViewById(R.id.shareButton).setOnClickListener(view -> {
            if (displayedAddress.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Bitcoin " + WalletPolicy.NETWORK_LABEL + " receiving address\nbitcoin:" + displayedAddress);
            startActivity(Intent.createChooser(intent, getString(R.string.share_address)));
        });
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        loadWallet();
    }
    @Override protected void onOperationFailed() { renderingAddress = ""; }
    @Override protected void refresh() {
        org.bitcoinj.wallet.Wallet wallet = service.requireWallet().getWallet();
        org.bitcoinj.crypto.ECKey key = wallet.currentReceiveKey();
        String address = key.toAddress(wallet.getActiveKeyChain().getOutputScriptType(),WalletPolicy.NETWORK).toString();
        if (displayedAddress.equals(address) || renderingAddress.equals(address)) return;
        renderingAddress = address;
        String publicKey = key.getPublicKeyAsHex();
        ((ImageView)findViewById(R.id.qrImage)).setImageDrawable(null);
        work(() -> {
            BitMatrix matrix = new QRCodeWriter().encode("bitcoin:" + address, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bitmap;
        }, bitmap -> {
            if (!service.requireWallet().getAddress().equals(address)) { renderingAddress = ""; refresh(); return; }
            displayedAddress = address; renderingAddress = "";
            displayedPublicKey = publicKey;
            ((TextView)findViewById(R.id.walletAddressText)).setText(address);
            ((TextView)findViewById(R.id.beneficiaryPublicKeyText)).setText(publicKey);
            ((ImageView)findViewById(R.id.qrImage)).setImageBitmap(bitmap);
        });
    }
}
