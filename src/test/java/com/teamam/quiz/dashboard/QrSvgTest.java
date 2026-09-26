package com.teamam.quiz.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

class QrSvgTest {

    // Guards against: a QR that renders but encodes the wrong join link (phones would open nothing).
    @Test
    void qrDecodesBackToTheJoinUrl() throws Exception {
        String url = "http://192.168.1.5:8080/student.html?s=12&c=AB12CD34";

        BitMatrix matrix = QrSvg.matrix(url);

        assertThat(decode(matrix)).isEqualTo(url);
    }

    // Guards against: the SVG being unusable in an <img> tag or dark-on-dark in dark mode.
    @Test
    void svgIsSelfContainedDarkOnWhite() {
        String svg = QrSvg.svg(QrSvg.matrix("http://10.0.0.2:8080/student.html?s=1&c=X"));

        assertThat(svg).startsWith("<svg").contains("xmlns=\"http://www.w3.org/2000/svg\"");
        assertThat(svg).contains("fill=\"#ffffff\"").contains("fill=\"#000000\"");
    }

    private static String decode(BitMatrix matrix) throws Exception {
        int scale = 4;
        int width = matrix.getWidth() * scale;
        int height = matrix.getHeight() * scale;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x / scale, y / scale) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        return new QRCodeReader().decode(bitmap).getText();
    }
}
