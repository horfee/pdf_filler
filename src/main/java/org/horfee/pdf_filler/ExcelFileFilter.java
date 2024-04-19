package org.horfee.pdf_filler;

import java.io.File;
import javax.swing.filechooser.FileFilter;

public class ExcelFileFilter extends FileFilter {

	@Override
	public String getDescription() {
		return "Excel files (*.xls, *.xlsx)";
	}
	
	@Override
	public boolean accept(File f) {
		return f.isDirectory() || (f.isFile() && f.exists() && (f.getAbsolutePath().endsWith(".xls") || f.getAbsolutePath().endsWith(".xlsx"))); 
	}
}
