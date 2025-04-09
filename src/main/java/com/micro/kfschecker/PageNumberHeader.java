package com.micro.kfschecker;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.InputStream;

public class PageNumberHeader extends PdfPageEventHelper {

    Font pageNumberFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
    Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new BaseColor(49, 143, 216));

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte canvas = writer.getDirectContent();

        // Číslo stránky vpravo
        Phrase pageNumberPhrase = new Phrase("Strana " + writer.getPageNumber(), pageNumberFont);
        ColumnText.showTextAligned(canvas,
                Element.ALIGN_RIGHT,
                pageNumberPhrase,
                document.right(),
                document.top() + 50, // Upraveno dolů
                0);

        // Logo vlevo
        InputStream imageStream = getClass().getResourceAsStream("/com/micro/kfschecker/img/imglogo-basic-color-nobg-rgb.png");
        if (imageStream != null) {
            try {
                Image logo = Image.getInstance(toByteArray(imageStream));
                logo.scaleToFit(80, 31);
                logo.setAbsolutePosition(document.left(), document.top() + 30); // Upraveno dolů
                canvas.addImage(logo);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    imageStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Nadpis vlevo vedle loga
        Phrase titlePhrase = new Phrase("KFS Checker", headerFont);
        ColumnText.showTextAligned(canvas,
                Element.ALIGN_LEFT,
                titlePhrase,
                document.left() + 90,
                document.top() + 45, // Upraveno dolů
                0);
    }

    private byte[] toByteArray(InputStream inputStream) throws java.io.IOException {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }
}