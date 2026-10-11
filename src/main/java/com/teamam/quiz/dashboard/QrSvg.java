package com.teamam.quiz.dashboard;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;

/** Draws a QR code as SVG on the server, so the classroom needs no internet for a QR library. */
final class QrSvg {

    private QrSvg() {
    }

    /** Turns a link into a grid of black/white QR squares (ZXing). */
    static BitMatrix matrix(String text) {
        try {
            // Size 0 = one matrix cell per QR module; the SVG scales it up crisply.
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        } catch (WriterException e) {
            throw new IllegalArgumentException("cannot encode QR: " + e.getMessage(), e);
        }
    }

    /** Always black on white: phone cameras need a light background, whatever the page theme. */
    static String svg(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height
                + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>"
                + "<path d=\"" + path + "\" fill=\"#000000\"/></svg>";
    }
}
