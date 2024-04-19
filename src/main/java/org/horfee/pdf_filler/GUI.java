package org.horfee.pdf_filler;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import org.apache.poi.hssf.util.HSSFColor.HSSFColorPredefined;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class GUI extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextField inputPDFFile;
	private JButton btnGenerateExcelFile;
	private JLabel lblNewLabel_1;
	private JTextField inputResponseFile;
	private JButton btnBrowseExcelFile;
	private JButton btnGeneratePdfFiles;
	private JProgressBar progressBar;
	private JLabel lblNewLabel_2;
	private JTextField outputFile;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					GUI frame = new GUI();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private Process invokeProcess(String... arguments) throws IOException {
		Path javaExecutablePath = Paths.get(System.getProperty("java.home"),"bin","java");
	
		String[] systemArgs = new String[]{javaExecutablePath.toString(), "-jar", "Contents/Resources/pdftk-all.jar" };
		String[] args = Stream.concat(Arrays.stream(systemArgs), Arrays.stream(arguments)).toArray(String[]::new);
		
		Process pr = Runtime.getRuntime().exec(args);
		return pr;
		
	}
	
	
	private void generateExcelFile() {
		progressBar.setString("Generating excel file in progress");
		progressBar.setValue(0);
		
		Path inputFile = Paths.get(inputPDFFile.getText());
		
		XSSFWorkbook workBook = new XSSFWorkbook();
		XSSFSheet sheet = workBook.createSheet("Input Values");
		
		XSSFCellStyle linkStyle = workBook.createCellStyle();
		XSSFFont linkFont = workBook.createFont();

        // Setting the Link Style
        linkFont.setUnderline(XSSFFont.U_SINGLE);
        linkFont.setColor(HSSFColorPredefined.BLUE.getIndex());
        linkStyle.setFont(linkFont);
		
		
		List<Object[]> fields = new ArrayList<>();
		
		
		try {
			//Path tmp = Files.createTempFile("", "");
			
			//JOptionPane.showMessageDialog(null, Paths.get(GUI.class.getResource("/pdftk-all.jar").toURI()).toString());
			
			// commandline = "/usr/local/bin/pdftk '" + inputPDFFile.getText() + "' dump_data_fields";
			
				
			Process pr = invokeProcess( inputPDFFile.getText(), "dump_data_fields");
			BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
			String str;
			List<String> lines = new ArrayList<>();
			while( (str = br.readLine()) != null) {
				if ( str.equals("---")) {
					if ( !lines.isEmpty() ) {
						String fieldName = lines.stream().filter( (s) -> {return s.contains("FieldName");}).findFirst().get();
						String fieldType = lines.stream().filter( (s) -> {return s.contains("FieldType");}).findFirst().get();
						fields.add(new Object[] {
								fieldName.substring("FieldName: ".length()), 
								fieldType.substring("FieldType: ".length())
						});
						lines.clear();	
					}
					
				} else {
					lines.add(str);
				}
				
			}
			int res = pr.waitFor();
			if ( res != 0 ) {
				progressBar.setString("Error during excel file generation");
				progressBar.setValue(4);
				JOptionPane.showMessageDialog(GUI.this, "An error happened invoking sub process.\n Error code is " + res, "Error during fields extraction", JOptionPane.ERROR_MESSAGE);
				return;
			}
			System.out.println(res);
		} catch (IOException | InterruptedException  e1) {
			progressBar.setString("Error during excel file generation");
			progressBar.setValue(4);
			e1.printStackTrace();
			JOptionPane.showMessageDialog(GUI.this, "An error happened invoking sub process.\n Refer to the logs for more information ", "Error during fields extraction", JOptionPane.ERROR_MESSAGE);
			try {
				workBook.close();
			} catch (IOException e) {
			}
			return;
		}

		
		int i = 0;
		for(Object[] field: fields) {
			XSSFRow row = sheet.createRow(i++);
			row.createCell(0).setCellValue((String)field[0]);	
		}
		
		Path outputFile = Paths.get(inputFile.getParent().toString(), inputFile.getFileName().toString() + ".xlsx");
		
		try(FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
			workBook.write(fos);
			workBook.close();
			fos.flush();
			fos.close();
			inputResponseFile.setText(outputFile.toString());
		
			progressBar.setString("Generating excel file done");
			progressBar.setValue(4);
			
			
			if ( JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(GUI.this, "Do you want to open the file ?", "Open Excel", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null)) {
				Desktop.getDesktop().open(outputFile.toFile());							
			}
			
		} catch (IOException e1) {
			e1.printStackTrace();
			progressBar.setString("Error during excel file generation");
			progressBar.setValue(4);
			JOptionPane.showMessageDialog(GUI.this, "An error happened. See log for details", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	
	private void fillPDF() {
		
		// Resolve the output file name
		// if the user already provided the desired path, use it as is, if not, generate one from the original input file
		Path outputFile;
		if ( GUI.this.outputFile.getText() == null || GUI.this.outputFile.getText().isBlank()) {
			String fileName = Paths.get(GUI.this.inputPDFFile.getText()).getFileName().toString();
			outputFile = Paths.get(Paths.get(GUI.this.inputPDFFile.getText()).getParent().toString(),fileName);
			GUI.this.outputFile.setText(outputFile.toString() + ".generated.pdf");
		}
		outputFile = Paths.get(GUI.this.outputFile.getText());
		
		
		try {
			
			// First step : generate FDF empty file
			GUI.this.progressBar.setValue(0);
			GUI.this.progressBar.setString("Generating fdf file");
			Path tmpFdf = Files.createTempFile("pdf_filler", ".fdf");
			
			Process pr = invokeProcess(inputPDFFile.getText(), "generate_fdf", "output", tmpFdf.toString());
			int res = pr.waitFor();
			if ( res != 0 ) {
				JOptionPane.showMessageDialog(GUI.this, "An error happened invoking sub process.\n Error code is " + res, "Error during fdf file generation", JOptionPane.ERROR_MESSAGE);
				BufferedReader br = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
				String str;
				while( (str = br.readLine()) != null ) {
					System.err.println(str);
				}
				br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
				while( (str = br.readLine()) != null ) {
					System.err.println(str);
				}
				return;
			}
			
			
			// Second step : substitute values in generated FDF in step 1 with actual values from Excel file
			Path tmp2Fdf = Files.createTempFile("pdf_filler", ".fdf");
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(tmpFdf.toFile())));
			PrintWriter writer = new PrintWriter(tmp2Fdf.toFile());
			String str;
			String fieldName = null;
			
			GUI.this.progressBar.setValue(1);
			GUI.this.progressBar.setString("Loading values from excel response file");
			List<Object[]> values = getFieldValuesFromExcelFile(Paths.get(GUI.this.inputResponseFile.getText()));
			
			GUI.this.progressBar.setValue(2);
			GUI.this.progressBar.setString("Filling fdf file");
			while ( (str = br.readLine()) != null ) {
				if ( fieldName != null ) {
					final String fFieldName = fieldName;
					fieldName = null;
					
					
					Optional<Object[]> value = values.stream().filter( (Object[] val) -> { return ((String)val[0]).equalsIgnoreCase(fFieldName);}).findAny();
					if ( value.isPresent() && value.get()[1] != null ) {
						writer.println("/V (" + value.get()[1].toString() + ")");
					} else {
						writer.println(str);								
					}
				} else {
					writer.println(str);
				}
				if ( str.matches("/T \\(.*\\)")) {
					fieldName = str.substring("/T (".length(), str.length() - 1);
				}
				
			}
			br.close();
			writer.close();
			
			
			// Thirs step : generate filled PDF
			GUI.this.progressBar.setValue(3);
			GUI.this.progressBar.setString("Generating PDF file");
			pr = invokeProcess( inputPDFFile.getText(), "fill_form", tmp2Fdf.toString(), "output", outputFile.toString() );
			res = pr.waitFor();
			if ( res != 0 ) {
				JOptionPane.showMessageDialog(GUI.this, "An error happened invoking sub process.\n Error code is " + res, "Error during filling phase", JOptionPane.ERROR_MESSAGE);
				return;
			}
			GUI.this.progressBar.setValue(4);
			GUI.this.progressBar.setString("PDF filled successfully.");
			if ( JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(GUI.this, "Do you want to open the file ?", "Open PDF", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null)) {
				Desktop.getDesktop().open(outputFile.toFile());
			}

		
		} catch (IOException | InterruptedException e1) {
			JOptionPane.showMessageDialog(GUI.this, "An error happened invoking sub process.\nPlease refer to the logs for more information", "Error during during filling phase", JOptionPane.ERROR_MESSAGE);
			e1.printStackTrace();
		}
	}
	
	private List<Object[]> getFieldValuesFromExcelFile(Path file) {
		List<Object[]> res = new ArrayList<>();
		try {
			XSSFWorkbook workBook = new XSSFWorkbook(file.toFile());
			XSSFSheet sheet = workBook.getSheet("Input Values");
			
			int i = 0;
			while ( sheet.getRow(i) != null && sheet.getRow(i).getCell(0) != null && sheet.getRow(i).getCell(0).getStringCellValue() != null ) {
				String fieldName = sheet.getRow(i).getCell(0).getStringCellValue();
				String fieldValue = sheet.getRow(i).getCell(1) != null ? sheet.getRow(i).getCell(1).getStringCellValue(): null;
				
				if ( fieldValue != null ) {
					res.add(new Object[] {
							fieldName,
							fieldValue
					});					
				}
				
				i++;
			}
		} catch (InvalidFormatException | IOException e) {
			JOptionPane.showMessageDialog(this, "An error happened during Excel file parsing.\nPlease refer to the logs for more information", "Error", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		
		return res;
			
	}
	
	/**
	 * Create the frame.
	 */
	public GUI() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setPreferredSize(new Dimension(768, 200));
		setSize(getPreferredSize());
		setMinimumSize(new Dimension(512, 200));
		setMaximumSize(new Dimension(10000, 200));
		setLocationRelativeTo(null);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		GridBagLayout gbl_contentPane = new GridBagLayout();
		gbl_contentPane.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0};
		gbl_contentPane.columnWeights = new double[]{1.0, 0.0, 0.0};
		gbl_contentPane.rowHeights = new int[] {0, 0, 0, 0, 0, 0, 0};
		gbl_contentPane.columnWidths = new int[] {0, 0, 0};
		contentPane.setLayout(gbl_contentPane);
		
		JLabel lblNewLabel = new JLabel("PDF file :");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.anchor = GridBagConstraints.NORTHWEST;
//		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.gridx = 0;
		gbc_lblNewLabel.gridy = 0;
		contentPane.add(lblNewLabel, gbc_lblNewLabel);
		
		inputPDFFile = new JTextField();
		GridBagConstraints gbc_inputPDFFile = new GridBagConstraints();
		gbc_inputPDFFile.fill = GridBagConstraints.HORIZONTAL;
//		gbc_inputPDFFile.insets = new Insets(0, 0, 5, 0);
		gbc_inputPDFFile.anchor = GridBagConstraints.NORTH;
		gbc_inputPDFFile.gridx = 0;
		gbc_inputPDFFile.gridy = 1;
		contentPane.add(inputPDFFile, gbc_inputPDFFile);
		inputPDFFile.setColumns(10);
		
		JButton browsePDFFile = new JButton("...");
		browsePDFFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				CompletableFuture.runAsync( () -> {
					JFileChooser fileChooser = new JFileChooser("Input file");
					fileChooser.setFileFilter(new PDFFileFilter());
					
					int res = fileChooser.showOpenDialog(GUI.this);
					if ( res == JFileChooser.APPROVE_OPTION ) {
						File f = fileChooser.getSelectedFile();
						inputPDFFile.setText(f.getAbsolutePath());
						//f.getAbsolutePath()
					}
				});
			}
		});
		GridBagConstraints gbc_browsePDFFile = new GridBagConstraints();
//		gbc_browsePDFFile.insets = new Insets(0, 0, 5, 0);//(0, 0, 0, 0);
		gbc_browsePDFFile.anchor = GridBagConstraints.WEST;
		gbc_browsePDFFile.gridx = 1;
		gbc_browsePDFFile.gridy = 1;
		contentPane.add(browsePDFFile, gbc_browsePDFFile);
		
		btnGenerateExcelFile = new JButton("Generate Excel file");
		btnGenerateExcelFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				CompletableFuture.runAsync( () -> {
					generateExcelFile();
				});
			}
		});
		GridBagConstraints gbc_btnGenerateExcelFile = new GridBagConstraints();
		gbc_btnGenerateExcelFile.anchor = GridBagConstraints.WEST;
//		gbc_btnGenerateExcelFile.insets = new Insets(0, 0, 5, 0);
		gbc_btnGenerateExcelFile.gridx = 2;
		gbc_btnGenerateExcelFile.gridy = 1;
		contentPane.add(btnGenerateExcelFile, gbc_btnGenerateExcelFile);
		
		lblNewLabel_1 = new JLabel("Excel file :");
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.WEST;
//		gbc_lblNewLabel_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_1.gridx = 0;
		gbc_lblNewLabel_1.gridy = 2;
		contentPane.add(lblNewLabel_1, gbc_lblNewLabel_1);
		
		inputResponseFile = new JTextField();
		GridBagConstraints gbc_inputResponseFile = new GridBagConstraints();
//		gbc_inputResponseFile.insets = new Insets(0, 0, 5, 0);
		gbc_inputResponseFile.fill = GridBagConstraints.HORIZONTAL;
		gbc_inputResponseFile.gridx = 0;
		gbc_inputResponseFile.gridy = 3;
		contentPane.add(inputResponseFile, gbc_inputResponseFile);
		inputResponseFile.setColumns(10);
		
		btnBrowseExcelFile = new JButton("...");
		btnBrowseExcelFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				CompletableFuture.runAsync( () -> {
					JFileChooser fileChooser = new JFileChooser("Excel input file");
					fileChooser.setFileFilter(new ExcelFileFilter());
					
					int res = fileChooser.showOpenDialog(GUI.this);
					if ( res == JFileChooser.APPROVE_OPTION ) {
						File f = fileChooser.getSelectedFile();
						inputResponseFile.setText(f.getAbsolutePath());
						//f.getAbsolutePath()
					}
				});
			}
			
		});
		GridBagConstraints gbc_btnBrowseExcelFile = new GridBagConstraints();
//		gbc_btnBrowseExcelFile.insets = new Insets(0, 0, 5, 0);
		gbc_btnBrowseExcelFile.anchor = GridBagConstraints.WEST;
		gbc_btnBrowseExcelFile.gridx = 1;
		gbc_btnBrowseExcelFile.gridy = 3;
		contentPane.add(btnBrowseExcelFile, gbc_btnBrowseExcelFile);
		
		lblNewLabel_2 = new JLabel("Output file :");
		GridBagConstraints gbc_lblNewLabel_2 = new GridBagConstraints();
		gbc_lblNewLabel_2.anchor = GridBagConstraints.WEST;
//		gbc_lblNewLabel_2.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_2.gridx = 0;
		gbc_lblNewLabel_2.gridy = 4;
		contentPane.add(lblNewLabel_2, gbc_lblNewLabel_2);
		
		outputFile = new JTextField();
		outputFile.setColumns(10);
		GridBagConstraints gbc_outputFile = new GridBagConstraints();
		gbc_outputFile.gridwidth = 2;
//		gbc_outputFile.insets = new Insets(0, 0, 5, 5);
		gbc_outputFile.fill = GridBagConstraints.HORIZONTAL;
		gbc_outputFile.gridx = 0;
		gbc_outputFile.gridy = 5;
		contentPane.add(outputFile, gbc_outputFile);
		
		btnGeneratePdfFiles = new JButton("Generate PDF file");
		btnGeneratePdfFiles.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				CompletableFuture.runAsync( () -> {
					fillPDF();
				});
					
			}
		});
		GridBagConstraints gbc_btnGeneratePdfFiles = new GridBagConstraints();
		gbc_btnGeneratePdfFiles.anchor = GridBagConstraints.WEST;
//		gbc_btnGeneratePdfFiles.insets = new Insets(0, 0, 5, 0);
		gbc_btnGeneratePdfFiles.gridx = 2;
		gbc_btnGeneratePdfFiles.gridy = 5;
		contentPane.add(btnGeneratePdfFiles, gbc_btnGeneratePdfFiles);
		
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setMaximum(4);
		progressBar.setString("");
		GridBagConstraints gbc_progressBar = new GridBagConstraints();
		gbc_progressBar.anchor = GridBagConstraints.NORTH;
		gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_progressBar.gridwidth = 3;
		gbc_progressBar.gridx = 0;
		gbc_progressBar.gridy = 6;
		contentPane.add(progressBar, gbc_progressBar);
	}

}
