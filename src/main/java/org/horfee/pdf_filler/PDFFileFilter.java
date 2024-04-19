package org.horfee.pdf_filler;

import java.io.File;
import javax.swing.filechooser.FileFilter;

public class PDFFileFilter extends FileFilter {

	@Override
	public String getDescription() {
		return "PDF files (*.pdf)";
	}
	
	@Override
	public boolean accept(File f) {
		return f.isDirectory() || (f.isFile() && f.exists() && f.getAbsolutePath().endsWith(".pdf")); 
	}
}
