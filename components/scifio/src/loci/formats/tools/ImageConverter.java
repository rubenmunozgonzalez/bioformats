/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2012 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package loci.formats.tools;

import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

import loci.common.DataTools;
import loci.common.DebugTools;
import loci.common.Location;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelFiller;
import loci.formats.ChannelMerger;
import loci.formats.ChannelSeparator;
import loci.formats.FilePattern;
import loci.formats.FileStitcher;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.IFormatWriter;
import loci.formats.ImageReader;
import loci.formats.ImageTools;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.MissingLibraryException;
import loci.formats.MinMaxCalculator;
import loci.formats.ReaderWrapper;
import loci.formats.UpgradeChecker;
import loci.formats.in.OMETiffReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.meta.MetadataStore;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import loci.formats.services.OMEXMLServiceImpl;
import loci.formats.tiff.IFD;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.Vector;

import ome.xml.model.Image;
import ome.xml.model.OME;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ImageConverter is a utility class for converting a file between formats.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/tools/ImageConverter.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/tools/ImageConverter.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public final class ImageConverter {

  private static int[] indexes;
  private static String[] reps;
  private static String[] syms;
  private static int nreps;

  // -- Constants --

  private static final Logger LOGGER =
    LoggerFactory.getLogger(ImageConverter.class);

  // -- Fields --

  private String in = null, out = null;
  private String map = null;
  private String compression = null;
  private boolean stitch = false, separate = false, merge = false, fill = false;
  private boolean bigtiff = false, group = true;
  private boolean printVersion = false;
  private boolean autoscale = false;
  private Boolean overwrite = null;
  private int series = -1;
  private int firstPlane = 0;
  private int lastPlane = Integer.MAX_VALUE;
  private int channel = -1, zSection = -1, timepoint = -1;
  private int xCoordinate = 0, yCoordinate = 0, width = 0, height = 0;

  private IFormatReader reader;
  private MinMaxCalculator minMax;

  // -- Constructor --

  private ImageConverter() { }

  private static void parseLabels(String infile){
    indexes = new int[384];
    reps = new String[384];
    syms = new String[384];
    try{
  	// Open the file that is the first 
  	// command line parameter
  	FileInputStream fstream = new FileInputStream(infile);
  	// Get the object of DataInputStream
  	DataInputStream in = new DataInputStream(fstream);
  	BufferedReader br = new BufferedReader(new InputStreamReader(in));
  	String line;
  	String[] columns;
        int n = 0;
  	//Read File Line By Line
  	while ((line = br.readLine()) != null)   {
  		// Print the content on the console
        	//System.out.println("line: " + line);
  		columns = line.split("--");
		indexes[nreps] = (int) Integer.valueOf(columns[0]);
		if (columns.length > 5){
			reps[nreps] = columns[4];	
			columns[5] = columns[5].replace(",","__");
			columns[5] = columns[5].replace("/","-");
			syms[nreps] = columns[5];	
		}
		else if (columns[4].equals("empty")){
			reps[nreps] = columns[4];	
			syms[nreps] = "mock";	
		}else{
			reps[nreps] = columns[4];	
			syms[nreps] = "unknown";	
		}
        	//System.out.println("reps [" + nreps + "]= " + reps[nreps]);
        	//System.out.println("syms [" + nreps + "]= " + syms[nreps]);
                nreps++;
  	}
        System.out.println("\n");
        System.out.println("CONVERSION RUNNING: Please wait until this window closes automatically\n");
        System.out.println("\n");
  	//Close the input stream
  	in.close();
   }catch (Exception e){//Catch exception if any
  	e.printStackTrace();
   }
  }

  // -- Utility methods --

  /** A utility method for converting a file from the command line. */
  public boolean testConvert(IFormatWriter writer, String[] args)
    throws FormatException, IOException
  {
    DebugTools.enableLogging("INFO");
    String in = null, out = null;
    String map = null;
    String compression = null;
    String infile = "";
    boolean stitch = false, separate = false, merge = false, fill = false;
    boolean bigtiff = false;
    int series = -1;
    int firstPlane = 0;
    long mid = 0;
    float timeLastLogged = 0;
    int total = 0;
    int num = 0;
    int numImages = 0;
    float read = 0;
    float write = 0;
    int first = 0;
    int last = 0;
    int lastPlane = Integer.MAX_VALUE;
    Vector delete = new Vector();
    String name = "";

    if (args != null) {
      for (int i=0; i<args.length; i++) {
        if (args[i].startsWith("-") && args.length > 1) {
          if (args[i].equals("-debug")) {
            DebugTools.enableLogging("DEBUG");
          }
          else if (args[i].equals("-stitch")) stitch = true;
          else if (args[i].equals("-separate")) separate = true;
          else if (args[i].equals("-merge")) merge = true;
          else if (args[i].equals("-expand")) fill = true;
          else if (args[i].equals("-bigtiff")) bigtiff = true;
          else if (args[i].equals("-map")) map = args[++i];
          else if (args[i].equals("-compression")) compression = args[++i];
          else if (args[i].equals("-nogroup")) group = false;
          else if (args[i].equals("-autoscale")) autoscale = true;
          else if (args[i].equals("-overwrite")) {
            overwrite = true;
          }
          else if (args[i].equals("-nooverwrite")) {
            overwrite = false;
          }
          else if (args[i].equals("-channel")) {
            channel = Integer.parseInt(args[++i]);
          }
          else if (args[i].equals("-z")) {
            zSection = Integer.parseInt(args[++i]);
          }
          else if (args[i].equals("-timepoint")) {
            timepoint = Integer.parseInt(args[++i]);
          }
          else if (args[i].equals("-infile")){
		infile = args[++i];
		parseLabels(infile);
	  }
          else if (args[i].equals("-series")) {
            try {
              series = Integer.parseInt(args[++i]);
            }
            catch (NumberFormatException exc) { }
          }
          else if (args[i].equals("-range")) {
            try {
              firstPlane = Integer.parseInt(args[++i]);
              lastPlane = Integer.parseInt(args[++i]) + 1;
            }
            catch (NumberFormatException exc) { }
          }
          else {
            LOGGER.error("Found unknown command flag: {}; exiting.", args[i]);
            return false;
          }
        }
        else {
          if (in == null) in = args[i];
          else if (out == null) out = args[i];
          else {
            LOGGER.error("Found unknown argument: {}; exiting.", args[i]);
            LOGGER.error("You should specify exactly one input file and " +
              "exactly one output file.");
            return false;
          }
        }
      }
    }
    if (in == null || out == null) {
      String[] s = {
        "To convert a file between formats, run:",
        "  bfconvert [-debug] [-stitch] [-separate] [-merge] [-expand]",
        "    [-bigtiff] [-compression codec] [-series series] [-map id]",
        "    [-range start end] in_file out_file",
        "",
        "      -debug: turn on debugging output",
        "     -stitch: stitch input files with similar names",
        "   -separate: split RGB images into separate channels",
        "      -merge: combine separate channels into RGB image",
        "     -expand: expand indexed color to RGB",
        "    -bigtiff: force BigTIFF files to be written",
        "-compression: specify the codec to use when saving images",
        "     -series: specify which image series to convert",
        "        -map: specify file on disk to which name should be mapped",
        "      -range: specify range of planes to convert (inclusive)",
        "    -nogroup: force multi-file datasets to be read as individual" +
        "              files",
        "  -autoscale: automatically adjust brightness and contrast before",
        "              converting; this may mean that the original pixel",
        "              values are not preserved",
        "  -overwrite: always overwrite the output file, if it already exists",
        "-nooverwrite: never overwrite the output file, if it already exists",
        "       -crop: crop images before converting; argument is 'x,y,w,h'",
        "    -channel: only convert the specified channel (indexed from 0)",
        "          -z: only convert the specified Z section (indexed from 0)",
        "  -timepoint: only convert the specified timepoint (indexed from 0)",
        "    -infile: specify microscope-in file to rename",
        "",
        "If any of the following patterns are present in out_file, they will",
        "be replaced with the indicated metadata value from the input file.",
        "",
        "   Pattern:\tMetadata value:",
        "   ---------------------------",
        "   " + FormatTools.SERIES_NUM + "\t\tseries index",
        "   " + FormatTools.SERIES_NAME + "\t\tseries name",
        "   " + FormatTools.CHANNEL_NUM + "\t\tchannel index",
        "   " + FormatTools.CHANNEL_NAME +"\t\tchannel name",
        "   " + FormatTools.Z_NUM + "\t\tZ index",
        "   " + FormatTools.T_NUM + "\t\tT index",
        "",
        "If any of these patterns are present, then the images to be saved",
        "will be split into multiple files.  For example, if the input file",
        "contains 5 Z sections and 3 timepoints, and out_file is",
        "",
        "  converted_Z" + FormatTools.Z_NUM + "_T" +
        FormatTools.T_NUM + ".tiff",
        "",
        "then 15 files will be created, with the names",
        "",
        "  converted_Z0_T0.tiff",
        "  converted_Z0_T1.tiff",
        "  converted_Z0_T2.tiff",
        "  converted_Z1_T0.tiff",
        "  ...",
        "  converted_Z4_T2.tiff",
        "",
        "Each file would have a single image plane."
      };
      for (int i=0; i<s.length; i++) LOGGER.info(s[i]);
      return false;
    }

    if (new Location(out).exists()) {
      if (overwrite == null) {
        LOGGER.warn("Output file {} exists.", out);
        LOGGER.warn("Do you want to overwrite it? ([y]/n)");
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        String choice = r.readLine().trim().toLowerCase();
        overwrite = !choice.startsWith("n");
      }
      if (!overwrite) {
        LOGGER.warn("Exiting; next time, please specify an output file that " +
          "does not exist.");
        return false;
      }
      else {
        new Location(out).delete();
      }
    }

    if (map != null) Location.mapId(in, map);

    long start = System.currentTimeMillis();
    LOGGER.info(in);
    reader = new ImageReader();

/* XXX
    boolean loop = true;

    do{
*/
    if (stitch) {
      reader = new FileStitcher(reader);
      Location f = new Location(in);
      String pat = null;
      if (!f.exists()) {
        pat = in;
      }
      else {
        pat = FilePattern.findPattern(f);
      }
      if (pat != null) in = pat;
    }
    if (separate) reader = new ChannelSeparator(reader);
    if (merge) reader = new ChannelMerger(reader);
    if (fill) reader = new ChannelFiller(reader);
    minMax = null;
    if (autoscale) {
      reader = new MinMaxCalculator(reader);
      minMax = (MinMaxCalculator) reader;
    }

    reader.setMetadataFiltered(true);
    reader.setOriginalMetadataPopulated(true);
    OMEXMLService service = null;
    try {
      ServiceFactory factory = new ServiceFactory();
      service = factory.getInstance(OMEXMLService.class);
      reader.setMetadataStore(service.createOMEXMLMetadata());
    }
    catch (DependencyException de) {
      throw new MissingLibraryException(OMEXMLServiceImpl.NO_OME_XML_MSG, de);
    }
    catch (ServiceException se) {
      throw new FormatException(se);
    }

    reader.setId(in);

    MetadataStore store = reader.getMetadataStore();
    MetadataTools.populatePixels(store, reader, false, false);

    boolean dimensionsSet = true;
    if (width == 0 || height == 0) {
      width = reader.getSizeX();
      height = reader.getSizeY();
      dimensionsSet = false;
    }

    if (store instanceof MetadataRetrieve) {
      if (series >= 0) {
        try {
          String xml = service.getOMEXML(service.asRetrieve(store));
          OME root = (OME) store.getRoot();
          Image exportImage = root.getImage(series);

          IMetadata meta = service.createOMEXMLMetadata(xml);
          OME newRoot = (OME) meta.getRoot();
          while (newRoot.sizeOfImageList() > 0) {
            newRoot.removeImage(newRoot.getImage(0));
          }

          newRoot.addImage(exportImage);
          meta.setRoot(newRoot);
          writer.setMetadataRetrieve((MetadataRetrieve) meta);
        }
        catch (ServiceException e) {
          throw new FormatException(e);
        }
      }
      else {
        writer.setMetadataRetrieve((MetadataRetrieve) store);
      }
    }
    writer.setWriteSequentially(true);

    if (writer instanceof TiffWriter) {
      ((TiffWriter) writer).setBigTiff(bigtiff);
    }
    else if (writer instanceof ImageWriter) {
      IFormatWriter w = ((ImageWriter) writer).getWriter(out);
      if (w instanceof TiffWriter) {
        ((TiffWriter) w).setBigTiff(bigtiff);
      }
    }

    String format = writer.getFormat();
    //LOGGER.info("[{}] -> {} [{}]",
    //  new Object[] {reader.getFormat(), out, format});
    mid = System.currentTimeMillis();

    total = 0;
    num = writer.canDoStacks() ? reader.getSeriesCount() : 1;
    read = 0; 
    write = 0;
    first = series == -1 ? 0 : series;
    last = series == -1 ? num : series + 1;
    timeLastLogged = System.currentTimeMillis();

    String platename = "";
    int index = 0;

    for (int q=first; q<last; q++) {
      reader.setSeries(q);

      if (!dimensionsSet) {
        width = reader.getSizeX();
        height = reader.getSizeY();
      }

      int writerSeries = series == -1 ? q : 0;
      writer.setSeries(writerSeries);
      writer.setInterleaved(reader.isInterleaved());
      writer.setValidBitsPerPixel(reader.getBitsPerPixel());
      numImages = writer.canDoStacks() ? reader.getImageCount() : 1;
      LOGGER.info("Image Count: " + reader.getImageCount());
      //loop = false;

      int startPlane = (int) Math.max(0, firstPlane);
      int endPlane = (int) Math.min(numImages, lastPlane);
      numImages = endPlane - startPlane;

      total += numImages;

      for (int i=startPlane; i<endPlane; i++) {
        long m = System.currentTimeMillis();
	long s = m;

        name = FormatTools.getFilename(q, i, reader, out);
        if (infile != ""){
		Pattern pattern = Pattern.compile(".*/W([0-9]+).*");
		Matcher matcher = pattern.matcher(name);

		if (matcher.find()) {
           	    index = (int) Integer.valueOf(matcher.group(1)) - 1;
		}
		else{
			pattern = Pattern.compile(".*\\\\W([0-9]+).*");
			matcher = pattern.matcher(name);
			if (matcher.find()) {
                    		index = (int) Integer.valueOf(matcher.group(1)) - 1;
                	}
		}
		pattern = Pattern.compile(".*/([^/]*)/W[0-9]+.*");
		matcher = pattern.matcher(name);

		if (matcher.find()) {
           	    platename = matcher.group(1);
		}
		else{
			pattern = Pattern.compile(".*\\\\([^/]*)\\\\W[0-9]+.*");
			matcher = pattern.matcher(name);
			if (matcher.find()) {
           	    		platename = matcher.group(1);
			}
		}
		
           	//System.out.println("platename = " + platename);
           	//System.out.println("int index = (int)Math.floor(" + q + " / " + "(int)Math.floor(" + last + " / " + nreps + "));");
		int tmp = Math.max(infile.lastIndexOf("\\"), infile.lastIndexOf("/"));
		//tmp = Math.max(tmp + 1, 0);
		//if (infile.lastIndexOf('.') > 0)
		//	platename = infile.substring(tmp, infile.lastIndexOf('.'));
		//else
		//	platename = infile.substring(tmp);

               	name = name.replace("platename", platename);
               	name = name.replace("reporter", reps[index]);
               	name = name.replace("symbol", syms[index]);
		//System.out.println("name: " + name);
		//System.out.println("infile: " + infile);
		//System.out.println("platename: " + platename);
		//System.out.println("index: " + index);
	}

        if (!(new File(name).exists())){
		//loop = true;
        	((new File(name)).getParentFile()).mkdirs();
        	writer.setId(name);
        	//File tmpfile = File.createTempFile("image",".ome.tif");
        	//writer.setId(tmpfile.getAbsolutePath());
        	if (compression != null) writer.setCompression(compression);

        	s = System.currentTimeMillis();
        	byte[] buf = reader.openBytes(i);
                byte[] empty = new byte[buf.length];
        	byte[][] lut = reader.get8BitLookupTable();
        	if (lut != null) {
          		IndexColorModel model = new IndexColorModel(8, lut[0].length,
            		lut[0], lut[1], lut[2]);
          		writer.setColorModel(model);
        	}

        	LOGGER.info(name);
        	writer.saveBytes(i - startPlane, buf);
      		if (Arrays.equals(empty, buf)){
        		LOGGER.info("Will delete: " + name);
			delete.add(name);
		}
		
        	//copyFile(tmpfile,new File(FormatTools.getFilename(q, i, reader, out)));
        	//tmpfile.delete();
        	long e = System.currentTimeMillis();
       		read += m - s;
       		write += e - m;

        	// log number of planes processed every second or so
        	if (i == numImages - 1 || (e - timeLastLogged) / 1000 > 0) {
          		int current = (i - startPlane) + 1;
          		int percent = 100 * current / numImages;
          		StringBuilder sb = new StringBuilder();
          		sb.append("\t");
          		int numSeries = last - first;
          		if (numSeries > 1) {
            			sb.append("Series ");
            			sb.append(q);
            			sb.append(": converted ");
          		}
          	else sb.append("Converted ");
            		//new Object[] {current, numImages, percent});
          		timeLastLogged = e;
        	}
	  }
	}
      }
      writer.close();
/*
    }while(loop);
*/
     
    while(delete.size() > 0){
	name = (String)delete.remove(0);
    	(new File(name)).delete();
    }

    LOGGER.info("[conversion done]");
    return true;

    //long end = System.currentTimeMillis();

    // output timing results
    //float sec = (end - start) / 1000f;
    //long initial = mid - start;
    //float readAvg = (float) read / total;
    //float writeAvg = (float) write / total;
    //LOGGER.info("{}s elapsed ({}+{}ms per plane, {}ms overhead)",
    //  new Object[] {sec, readAvg, writeAvg, initial});
  }

  // -- Helper methods --

  private long convertPlane(IFormatWriter writer, int index, int startPlane)
    throws FormatException, IOException
  {
    if (width * height >= 4096 * 4096) {
      // this is a "big image", so we will attempt to convert it one tile
      // at a time

      if ((writer instanceof TiffWriter) || ((writer instanceof ImageWriter) &&
        (((ImageWriter) writer).getWriter(out) instanceof TiffWriter)))
      {
        return convertTilePlane(writer, index, startPlane);
      }
    }

    byte[] buf =
      reader.openBytes(index, xCoordinate, yCoordinate, width, height);

    autoscalePlane(buf, index);
    applyLUT(writer);
    long m = System.currentTimeMillis();
    writer.saveBytes(index - startPlane, buf);
    return m;
  }

  private long convertTilePlane(IFormatWriter writer, int index, int startPlane)
    throws FormatException, IOException
  {
    int w = width;
    int h = 1;
    int nXTiles = width / w;
    int nYTiles = height / h;

    IFD ifd = new IFD();

    Long m = null;
    for (int y=0; y<nYTiles; y++) {
      for (int x=0; x<nXTiles; x++) {
        int tileX = xCoordinate + x * w;
        int tileY = yCoordinate + y * h;
        int tileWidth = x < nXTiles - 1 ? w : w - (width % w);
        int tileHeight = y < nYTiles - 1 ? h : h - (height % h);
        byte[] buf =
          reader.openBytes(index, tileX, tileY, tileWidth, tileHeight);

        autoscalePlane(buf, index);
        applyLUT(writer);
        if (m == null) {
          m = System.currentTimeMillis();
        }
        ifd.put(IFD.TILE_WIDTH, tileWidth);
        ifd.put(IFD.TILE_LENGTH, tileHeight);

        if (writer instanceof TiffWriter) {
          ((TiffWriter) writer).saveBytes(index - startPlane, buf,
            ifd, tileX, tileY, tileWidth, tileHeight);
        }
        else if (writer instanceof ImageWriter) {
          IFormatWriter baseWriter = ((ImageWriter) writer).getWriter(out);
          if (baseWriter instanceof TiffWriter) {
            ((TiffWriter) baseWriter).saveBytes(index - startPlane, buf, ifd,
              tileX, tileY, tileWidth, tileHeight);
          }
        }
      }
    }
    return m;
  }

  private void autoscalePlane(byte[] buf, int index)
    throws FormatException, IOException
  {
    if (autoscale) {
      Double min = null;
      Double max = null;

      Double[] planeMin = minMax.getPlaneMinimum(index);
      Double[] planeMax = minMax.getPlaneMaximum(index);

      if (planeMin != null && planeMax != null) {
        min = planeMin[0];
        max = planeMax[0];

        for (int j=1; j<planeMin.length; j++) {
          if (planeMin[j].doubleValue() < min.doubleValue()) {
            min = planeMin[j];
          }
          if (planeMax[j].doubleValue() < max.doubleValue()) {
            max = planeMax[j];
          }
        }
      }

      int pixelType = reader.getPixelType();
      int bpp = FormatTools.getBytesPerPixel(pixelType);
      boolean floatingPoint = FormatTools.isFloatingPoint(pixelType);
      Object pix = DataTools.makeDataArray(buf, bpp, floatingPoint,
        reader.isLittleEndian());
      byte[][] b = ImageTools.make24Bits(pix, width, height,
        reader.isInterleaved(), false, min, max);

      int channelCount = reader.getRGBChannelCount();
      int copyComponents = (int) Math.min(channelCount, b.length);

      buf = new byte[channelCount * b[0].length];
      for (int j=0; j<copyComponents; j++) {
        System.arraycopy(b[j], 0, buf, b[0].length * j, b[0].length);
      }
    }
  }

  private void applyLUT(IFormatWriter writer)
    throws FormatException, IOException
  {
    byte[][] lut = reader.get8BitLookupTable();
    if (lut != null) {
      IndexColorModel model = new IndexColorModel(8, lut[0].length,
        lut[0], lut[1], lut[2]);
      writer.setColorModel(model);
    }
  }

  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    UpgradeChecker checker = new UpgradeChecker();
    boolean canUpgrade =
      checker.newVersionAvailable(UpgradeChecker.DEFAULT_CALLER);
    if (canUpgrade) {
      LOGGER.info("*** A new stable version is available. ***");
      LOGGER.info("*** Install the new version using:     ***");
      LOGGER.info("***   'upgradechecker -install'        ***");
    }

    ImageConverter converter = new ImageConverter();
    if (!converter.testConvert(new ImageWriter(), args)) System.exit(1);
    System.exit(0);
  }

  public static void copyFile(File source, File dest) throws IOException {
	
		if(!dest.exists()) {
			dest.createNewFile();
		}
        InputStream in = null;
        OutputStream out = null;
        try {
        	in = new FileInputStream(source);
        	out = new FileOutputStream(dest);
    
	        // Transfer bytes from in to out
	        byte[] buf = new byte[1024];
	        int len;
	        while ((len = in.read(buf)) > 0) {
	            out.write(buf, 0, len);
	        }
        }
        finally {
        	in.close();
            out.close();
        }
        
  }

}
