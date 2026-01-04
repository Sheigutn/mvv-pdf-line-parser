package com.github.sheigutn.mvvpdflineparser;

import org.apache.pdfbox.contentstream.operator.color.*;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

// Credit: https://stackoverflow.com/a/67026429
public class PDFTextStripperSuper extends PDFTextStripper {
    boolean newLine = true;
    
    public PDFTextStripperSuper() throws IOException {
        addOperator(new SetStrokingColorSpace(this));
        addOperator(new SetNonStrokingColorSpace(this));
        addOperator(new SetStrokingDeviceCMYKColor(this));
        addOperator(new SetNonStrokingDeviceCMYKColor(this));
        addOperator(new SetNonStrokingDeviceRGBColor(this));
        addOperator(new SetStrokingDeviceRGBColor(this));
        addOperator(new SetNonStrokingDeviceGrayColor(this));
        addOperator(new SetStrokingDeviceGrayColor(this));
        addOperator(new SetStrokingColor(this));
        addOperator(new SetStrokingColorN(this));
        addOperator(new SetNonStrokingColor(this));
        addOperator(new SetNonStrokingColorN(this));
    }
    
    @Override
    protected void startPage(PDPage page) throws IOException {
        newLine = true;
        super.startPage(page);
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        newLine = true;
        super.writeLineSeparator();
    }
}