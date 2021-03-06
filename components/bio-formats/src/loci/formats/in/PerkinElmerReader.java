/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2012 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package loci.formats.in;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.DateTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

import ome.xml.model.primitives.PositiveFloat;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

/**
 * PerkinElmerReader is the file format reader for PerkinElmer files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/PerkinElmerReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/PerkinElmerReader.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 */
public class PerkinElmerReader extends FormatReader {

  // -- Constants --

  public static final String[] CFG_SUFFIX = {"cfg"};
  public static final String[] ANO_SUFFIX = {"ano"};
  public static final String[] REC_SUFFIX = {"rec"};
  public static final String[] TIM_SUFFIX = {"tim"};
  public static final String[] CSV_SUFFIX = {"csv"};
  public static final String[] ZPO_SUFFIX = {"zpo"};
  public static final String[] HTM_SUFFIX = {"htm"};

  public static final String HTML_REGEX =
    "<p>|</p>|<br>|<hr>|<b>|</b>|<HTML>|<HEAD>|</HTML>|" +
    "</HEAD>|<h1>|</h1>|<HR>|</body>";

  public static final String DATE_FORMAT = "HH:mm:ss (MM/dd/yyyy)";

  // -- Fields --

  /** Helper reader. */
  protected MinimalTiffReader tiff;

  /** TIFF files to open. */
  protected String[] files;

  /** Flag indicating that the image data is in TIFF format. */
  private boolean isTiff = true;

  /** List of all files to open */
  private Vector<String> allFiles;

  private String details, sliceSpace;

  private double pixelSizeX = 1, pixelSizeY = 1;
  private String finishTime = null, startTime = null;
  private double originX = 0, originY = 0, originZ = 0;

  // -- Constructor --

  /** Constructs a new PerkinElmer reader. */
  public PerkinElmerReader() {
    super("PerkinElmer", new String[] {
      "ano", "cfg", "csv", "htm", "rec", "tim", "zpo", "tif"});
    domains = new String[] {FormatTools.LM_DOMAIN};
    hasCompanionFiles = true;
    datasetDescription = "One .htm file, several other metadata files " +
      "(.tim, .ano, .csv, ...) and either .tif files or .2, .3, .4, etc. files";
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isSingleFile(String) */
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    if (!open) return false; // not allowed to touch the file system

    if (checkSuffix(name, "cfg")) {
      // must contain the word "Ultraview"
      try {
        String check = DataTools.readFile(name);
        if (check.indexOf("Ultraview") == -1) return false;
      }
      catch (IOException e) { }
    }

    String ext = name;
    if (ext.indexOf(".") != -1) ext = ext.substring(ext.lastIndexOf(".") + 1);
    boolean binFile = true;
    try {
      Integer.parseInt(ext, 16);
    }
    catch (NumberFormatException e) {
      ext = ext.toLowerCase();
      if (!ext.equals("tif") && !ext.equals("tiff")) binFile = false;
    }

    Location baseFile = new Location(name).getAbsoluteFile();
    String prefix = baseFile.getParent() + File.separator;

    String namePrefix = baseFile.getName();
    if (namePrefix.indexOf(".") != -1) {
      namePrefix = namePrefix.substring(0, namePrefix.lastIndexOf("."));
    }
    if (namePrefix.indexOf("_") != -1 && binFile) {
      namePrefix = namePrefix.substring(0, namePrefix.lastIndexOf("_"));
    }
    prefix += namePrefix;

    Location htmlFile = new Location(prefix + ".htm");
    if (ext.toLowerCase().equals("htm")) {
      htmlFile = new Location(name).getAbsoluteFile();
    }
    if (!htmlFile.exists()) {
      htmlFile = new Location(prefix + ".HTM");
      while (!htmlFile.exists() && prefix.indexOf("_") != -1) {
        prefix = prefix.substring(0, prefix.lastIndexOf("_"));
        htmlFile = new Location(prefix + ".htm");
        if (!htmlFile.exists()) htmlFile = new Location(prefix + ".HTM");
      }
    }

    return htmlFile.exists() && (binFile || super.isThisType(name, false));
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  /* @see loci.formats.IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    if (isTiff && tiff != null) {
      return tiff.get8BitLookupTable();
    }
    return null;
  }

  /* @see loci.formats.IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    if (isTiff && tiff != null) {
      return tiff.get16BitLookupTable();
    }
    return null;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);
    if (isTiff) {
      tiff.setId(files[no / getSizeC()]);
      return tiff.openBytes(0, buf, x, y, w, h);
    }

    RandomAccessInputStream ras = new RandomAccessInputStream(files[no]);
    ras.seek(6);
    readPlane(ras, x, y, w, h, buf);
    ras.close();
    return buf;
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);
    if (noPixels) {
      Vector<String> files = new Vector<String>();
      if (isTiff) {
        for (String f : allFiles) {
          if (!checkSuffix(f, new String[] {"tif", "tiff"})) {
            files.add(f);
          }
        }
      }
      else {
        for (String f : allFiles) {
          String ext = f.substring(f.lastIndexOf(".") + 1);
          try {
            Integer.parseInt(ext, 16);
          }
          catch (NumberFormatException e) { files.add(f); }
        }
      }
      return files.toArray(new String[files.size()]);
    }
    return allFiles.toArray(new String[allFiles.size()]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (tiff != null) tiff.close(fileOnly);
    if (!fileOnly) {
      tiff = null;
      allFiles = null;
      files = null;
      details = sliceSpace = null;
      isTiff = true;
      pixelSizeX = pixelSizeY = 1f;
      finishTime = startTime = null;
      originX = originY = originZ = 0f;
    }
  }

  /* @see loci.formats.IFormatReader#getOptimalTileWidth() */
  public int getOptimalTileWidth() {
    FormatTools.assertId(currentId, true, 1);
    if (isTiff) {
      return tiff.getOptimalTileWidth();
    }
    return super.getOptimalTileWidth();
  }

  /* @see loci.formats.IFormatReader#getOptimalTileHeight() */
  public int getOptimalTileHeight() {
    FormatTools.assertId(currentId, true, 1);
    if (isTiff) {
      return tiff.getOptimalTileHeight();
    }
    return super.getOptimalTileHeight();
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (currentId != null && (id.equals(currentId) || isUsedFile(id))) return;

    LOGGER.info("Finding HTML companion file");

    // always init on the HTML file - this prevents complications with
    // initializing the image files

    if (!checkSuffix(id, HTM_SUFFIX)) {
      Location parent = new Location(id).getAbsoluteFile().getParentFile();
      String[] ls = parent.list();
      for (String file : ls) {
        if (checkSuffix(file, HTM_SUFFIX) && !file.startsWith(".")) {
          id = new Location(parent.getAbsolutePath(), file).getAbsolutePath();
          break;
        }
      }
    }

    super.initFile(id);

    allFiles = new Vector<String>();

    // get the working directory
    Location tmpFile = new Location(id).getAbsoluteFile();
    Location workingDir = tmpFile.getParentFile();
    if (workingDir == null) workingDir = new Location(".");
    String workingDirPath = workingDir.getPath();
    if (!workingDirPath.equals("")) workingDirPath += File.separator;
    String[] ls = workingDir.list(true);
    if (!new Location(id).exists()) {
      ls = Location.getIdMap().keySet().toArray(new String[0]);
      workingDirPath = "";
    }

    LOGGER.info("Searching for all metadata companion files");

    // check if we have any of the required header file types

    String cfgFile = null, anoFile = null, recFile = null;
    String timFile = null, csvFile = null, zpoFile = null;
    String htmFile = null;

    int filesPt = 0;
    files = new String[ls.length];

    int dot = id.lastIndexOf(".");
    String check = dot < 0 ? id : id.substring(0, dot);
    check = check.substring(check.lastIndexOf(File.separator) + 1);

    // locate appropriate .tim, .csv, .zpo, .htm and .tif files

    String prefix = null;

    for (int i=0; i<ls.length; i++) {
      // make sure that the file has a name similar to the name of the
      // specified file

      int d = ls[i].lastIndexOf(".");
      while (d == -1 && i < ls.length - 1) {
        i++;
        d = ls[i].lastIndexOf(".");
      }
      String s = d < 0 ? ls[i] : ls[i].substring(0, d);

      if (s.startsWith(check) || check.startsWith(s) ||
        ((prefix != null) && (s.startsWith(prefix))))
      {
        prefix = ls[i].substring(0, d);
        if (cfgFile == null && checkSuffix(ls[i], CFG_SUFFIX)) cfgFile = ls[i];
        if (anoFile == null && checkSuffix(ls[i], ANO_SUFFIX)) anoFile = ls[i];
        if (recFile == null && checkSuffix(ls[i], REC_SUFFIX)) recFile = ls[i];
        if (timFile == null && checkSuffix(ls[i], TIM_SUFFIX)) timFile = ls[i];
        if (csvFile == null && checkSuffix(ls[i], CSV_SUFFIX)) csvFile = ls[i];
        if (zpoFile == null && checkSuffix(ls[i], ZPO_SUFFIX)) zpoFile = ls[i];
        if (htmFile == null && checkSuffix(ls[i], HTM_SUFFIX)) htmFile = ls[i];

        if (checkSuffix(ls[i], TiffReader.TIFF_SUFFIXES)) {
          files[filesPt++] = workingDirPath + ls[i];
        }

        try {
          String ext = ls[i].substring(ls[i].lastIndexOf(".") + 1);
          Integer.parseInt(ext, 16);
          isTiff = false;
          files[filesPt++] = workingDirPath + ls[i];
        }
        catch (NumberFormatException exc) {
          LOGGER.debug("Failed to parse file extension", exc);
        }
      }
    }

    // re-order the files

    String[] tempFiles = files;
    files = new String[filesPt];

    // determine the number of different extensions we have

    LOGGER.info("Finding image files");

    Vector<String> foundExts = new Vector<String>();
    for (int i=0; i<filesPt; i++) {
      String ext = tempFiles[i].substring(tempFiles[i].lastIndexOf(".") + 1);
      if (!foundExts.contains(ext)) {
        foundExts.add(ext);
      }
    }
    int extCount = foundExts.size();
    foundExts = null;

    Vector<String> extSet = new Vector<String>();
    for (int i=0; i<filesPt; i+=extCount) {
      for (int j=0; j<extCount; j++) {
        String file = tempFiles[i + j];
        if (extSet.size() == 0) extSet.add(file);
        else {
          if (file == null) continue;
          String ext = file.substring(file.lastIndexOf(".") + 1);
          int extNum = Integer.parseInt(ext, 16);

          int insert = -1;
          int pos = 0;
          while (insert == -1 && pos < extSet.size()) {
            String posString = extSet.get(pos);
            posString = posString.substring(posString.lastIndexOf(".") + 1);
            int posNum = Integer.parseInt(posString, 16);

            if (extNum < posNum) insert = pos;
            pos++;
          }
          if (insert == -1) extSet.add(tempFiles[i + j]);
          else extSet.add(insert, tempFiles[i + j]);
        }
      }

      int length = (int) Math.min(extCount, extSet.size());
      for (int j=0; j<length; j++) {
        files[i + j] = extSet.get(j);
      }
      extSet.clear();
    }

    allFiles.addAll(Arrays.asList(files));

    sortFiles();

    core[0].imageCount = files.length;

    tiff = new MinimalTiffReader();

    // we always parse the .tim and .htm files if they exist, along with
    // either the .csv file or the .zpo file

    LOGGER.info("Parsing metadata values");

    addUsedFile(workingDirPath, cfgFile);
    addUsedFile(workingDirPath, anoFile);
    addUsedFile(workingDirPath, recFile);
    addUsedFile(workingDirPath, timFile);
    if (timFile != null) timFile = allFiles.get(allFiles.size() - 1);
    addUsedFile(workingDirPath, csvFile);
    if (csvFile != null) csvFile = allFiles.get(allFiles.size() - 1);
    addUsedFile(workingDirPath, zpoFile);
    if (zpoFile != null) zpoFile = allFiles.get(allFiles.size() - 1);
    addUsedFile(workingDirPath, htmFile);
    if (htmFile != null) htmFile = allFiles.get(allFiles.size() - 1);

    if (timFile != null) parseTimFile(timFile);
    if (csvFile != null) parseCSVFile(csvFile);
    if (zpoFile != null && csvFile == null) parseZpoFile(zpoFile);

    // be aggressive about parsing the HTML file, since it's the only one that
    // explicitly defines the number of wavelengths and timepoints

    Vector<Double> exposureTimes = new Vector<Double>();
    Vector<Double> zPositions = new Vector<Double>();
    Vector<Integer> emWaves = new Vector<Integer>();
    Vector<Integer> exWaves = new Vector<Integer>();

    if (htmFile != null) {
      String[] tokens = DataTools.readFile(htmFile).split(HTML_REGEX);

      for (int j=0; j<tokens.length; j++) {
        if (tokens[j].indexOf("<") != -1) tokens[j] = "";
      }

      for (int j=0; j<tokens.length-1; j+=2) {
        if (tokens[j].indexOf("Exposure") != -1) {
          addGlobalMeta("Camera Data " + tokens[j].charAt(13), tokens[j]);

          int ndx = tokens[j].indexOf("Exposure") + 9;
          String exposure =
            tokens[j].substring(ndx, tokens[j].indexOf(" ", ndx)).trim();
          if (exposure.endsWith(",")) {
            exposure = exposure.substring(0, exposure.length() - 1);
          }
          exposureTimes.add(new Double(Double.parseDouble(exposure) / 1000));

          if (tokens[j].indexOf("nm") != -1) {
            int nmIndex = tokens[j].indexOf("nm");
            int paren = tokens[j].lastIndexOf("(", nmIndex);
            int slash = tokens[j].lastIndexOf("/", nmIndex);
            if (slash == -1) slash = nmIndex;
            emWaves.add(
              new Integer(tokens[j].substring(paren + 1, slash).trim()));
            if (tokens[j].indexOf("nm", nmIndex + 3) != -1) {
              nmIndex = tokens[j].indexOf("nm", nmIndex + 3);
              paren = tokens[j].lastIndexOf(" ", nmIndex);
              slash = tokens[j].lastIndexOf("/", nmIndex);
              if (slash == -1) slash = nmIndex + 2;
              exWaves.add(
                new Integer(tokens[j].substring(paren + 1, slash).trim()));
            }
          }

          j--;
        }
        else if (tokens[j + 1].trim().equals("Slice Z positions")) {
          for (int q=j + 2; q<tokens.length; q++) {
            if (!tokens[q].trim().equals("")) {
              try {
                zPositions.add(new Double(tokens[q].trim()));
              }
              catch (NumberFormatException e) { }
            }
          }
        }
        else if (!tokens[j].trim().equals("")) {
          tokens[j] = tokens[j].trim();
          tokens[j + 1] = tokens[j + 1].trim();
          parseKeyValue(tokens[j], tokens[j + 1]);
        }
      }
    }
    else {
      throw new FormatException("Valid header files not found.");
    }

    // parse details to get number of wavelengths and timepoints

    if (details != null) {
      String[] tokens = details.split("\\s");
      int n = 0;
      for (String token : tokens) {
        if (token.equals("Wavelengths")) core[0].sizeC = n;
        else if (token.equals("Frames")) core[0].sizeT = n;
        else if (token.equals("Slices")) core[0].sizeZ = n;
        try {
          n = Integer.parseInt(token);
        }
        catch (NumberFormatException e) { n = 0; }
      }
    }

    LOGGER.info("Populating metadata");

    if (files.length == 0) {
      throw new FormatException("TIFF files not found.");
    }

    if (isTiff) {
      tiff.setId(files[0]);
      core[0].pixelType = tiff.getPixelType();
    }
    else {
      RandomAccessInputStream tmp = new RandomAccessInputStream(files[0]);
      int bpp = (int) (tmp.length() - 6) / (getSizeX() * getSizeY());
      tmp.close();
      if (bpp % 3 == 0) bpp /= 3;
      core[0].pixelType = FormatTools.pixelTypeFromBytes(bpp, false, false);
    }

    if (getSizeZ() <= 0) core[0].sizeZ = 1;
    if (getSizeC() <= 0) core[0].sizeC = 1;

    if (getSizeT() <= 0) {
      core[0].sizeT = getImageCount() / (getSizeZ() * getSizeC());
    }
    else {
      core[0].imageCount = getSizeZ() * getSizeC() * getSizeT();
      if (getImageCount() > files.length) {
        core[0].imageCount = files.length;
        core[0].sizeT = getImageCount() / (getSizeZ() * getSizeC());
      }
    }

    // throw away files, if necessary
    removeExtraFiles();

    core[0].dimensionOrder = "XYCTZ";
    core[0].rgb = isTiff ? tiff.isRGB() : false;
    core[0].interleaved = false;
    core[0].littleEndian = isTiff ? tiff.isLittleEndian() : true;
    core[0].metadataComplete = true;
    core[0].indexed = isTiff ? tiff.isIndexed() : false;
    core[0].falseColor = false;

    // Populate metadata store

    // The metadata store we're working with.
    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this, true);

    // populate Image element
    if (finishTime != null) {
      finishTime = DateTools.formatDate(finishTime, DATE_FORMAT);
      if (finishTime != null) {
        store.setImageAcquisitionDate(new Timestamp(finishTime), 0);
      }
    }

    if (getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {
      // populate Dimensions element
      if (pixelSizeX > 0) {
        store.setPixelsPhysicalSizeX(new PositiveFloat(pixelSizeX), 0);
      }
      else {
        LOGGER.warn("Expected positive value for PhysicalSizeX; got {}",
          pixelSizeX);
      }
      if (pixelSizeY > 0) {
        store.setPixelsPhysicalSizeY(new PositiveFloat(pixelSizeY), 0);
      }
      else {
        LOGGER.warn("Expected positive value for PhysicalSizeY; got {}",
          pixelSizeY);
      }

      // link Instrument and Image
      String instrumentID = MetadataTools.createLSID("Instrument", 0);
      store.setInstrumentID(instrumentID, 0);
      store.setImageInstrumentRef(instrumentID, 0);

      // populate LogicalChannel element
      for (int i=0; i<getEffectiveSizeC(); i++) {
        if (i < emWaves.size()) {
          if (emWaves.get(i) > 0) {
            store.setChannelEmissionWavelength(
              new PositiveInteger(emWaves.get(i)), 0, i);
          }
          else {
            LOGGER.warn(
              "Expected positive value for EmissionWavelength; got {}",
              emWaves.get(i));
          }
        }
        if (i < exWaves.size()) {
          if (exWaves.get(i) > 0) {
            store.setChannelExcitationWavelength(
              new PositiveInteger(exWaves.get(i)), 0, i);
          }
          else {
            LOGGER.warn(
              "Expected positive value for ExcitationWavelength; got {}",
              exWaves.get(i));
          }
        }
      }

      // populate PlaneTiming and StagePosition

      long start = 0, end = 0;
      if (startTime != null) {
        start = DateTools.getTime(startTime, DATE_FORMAT);
      }
      if (finishTime != null) {
        end = DateTools.getTime(finishTime, DateTools.ISO8601_FORMAT);
      }

      double secondsPerPlane = (double) (end - start) / getImageCount() / 1000;

      for (int i=0; i<getImageCount(); i++) {
        int[] zct = getZCTCoords(i);
        store.setPlaneDeltaT(i * secondsPerPlane, 0, i);
        if (zct[1] < exposureTimes.size()) {
          store.setPlaneExposureTime(exposureTimes.get(zct[1]), 0, i);
        }

        if (zct[0] < zPositions.size()) {
          store.setPlanePositionX(0.0, 0, i);
          store.setPlanePositionY(0.0, 0, i);
          store.setPlanePositionZ(zPositions.get(zct[0]), 0, i);
        }
      }
    }
  }

  // -- Helper methods --

  private void parseKeyValue(String key, String value) {
    if (key == null || value == null) return;
    addGlobalMeta(key, value);
    try {
      if (key.equals("Image Width")) {
        core[0].sizeX = Integer.parseInt(value);
      }
      else if (key.equals("Image Length")) {
        core[0].sizeY = Integer.parseInt(value);
      }
      else if (key.equals("Number of slices")) {
        core[0].sizeZ = Integer.parseInt(value);
      }
      else if (key.equals("Experiment details:")) details = value;
      else if (key.equals("Z slice space")) sliceSpace = value;
      else if (key.equals("Pixel Size X")) {
        pixelSizeX = Double.parseDouble(value);
      }
      else if (key.equals("Pixel Size Y")) {
        pixelSizeY = Double.parseDouble(value);
      }
      else if (key.equals("Finish Time:")) finishTime = value;
      else if (key.equals("Start Time:")) startTime = value;
      else if (key.equals("Origin X")) {
        originX = Double.parseDouble(value);
      }
      else if (key.equals("Origin Y")) {
        originY = Double.parseDouble(value);
      }
      else if (key.equals("Origin Z")) {
        originZ = Double.parseDouble(value);
      }
      else if (key.equals("SubfileType X")) {
        core[0].bitsPerPixel = Integer.parseInt(value);
      }
    }
    catch (NumberFormatException exc) {
      LOGGER.debug("", exc);
    }
  }

  /** Add the given file to the used files list. */
  private void addUsedFile(String workingDirPath, String file) {
    if (file == null) return;
    Location f = new Location(workingDirPath, file);
    if (!workingDirPath.equals("")) allFiles.add(f.getAbsolutePath());
    else allFiles.add(file);
  }

  private void sortFiles() {
   if (isTiff) Arrays.sort(files);
    else {
      Comparator<String> c = new Comparator<String>() {
        public int compare(String s1, String s2) {
          String prefix1 = s1, prefix2 = s2, suffix1 = s1, suffix2 = s2;
          if (s1.indexOf(".") != -1) {
            prefix1 = s1.substring(0, s1.lastIndexOf("."));
            suffix1 = s1.substring(s1.lastIndexOf(".") + 1);
          }
          if (s2.indexOf(".") != -1) {
            prefix2 = s2.substring(0, s2.lastIndexOf("."));
            suffix2 = s2.substring(s2.lastIndexOf(".") + 1);
          }
          int cmp = prefix1.compareTo(prefix2);
          if (cmp != 0) return cmp;
          return Integer.parseInt(suffix1, 16) - Integer.parseInt(suffix2, 16);
        }
      };
      Arrays.sort(files, c);
    }
  }

  private void removeExtraFiles() {
    int calcCount = getSizeZ() * getEffectiveSizeC() * getSizeT();
    if (files.length > getImageCount() || getImageCount() != calcCount) {
      LOGGER.info("Removing extraneous files");
      String[] tmpFiles = files;
      int imageCount = (int) Math.min(getImageCount(), calcCount);
      files = new String[imageCount];

      Hashtable<String, Integer> zSections = new Hashtable<String, Integer>();
      for (int i=0; i<tmpFiles.length; i++) {
        int underscore = tmpFiles[i].lastIndexOf("_");
        int dotIndex = tmpFiles[i].lastIndexOf(".");
        String z = tmpFiles[i].substring(underscore + 1, dotIndex);
        if (zSections.get(z) == null) zSections.put(z, new Integer(1));
        else {
          int count = zSections.get(z).intValue() + 1;
          zSections.put(z, new Integer(count));
        }
      }

      int nextFile = 0;
      int oldFile = 0;
      Arrays.sort(tmpFiles, new PEComparator());
      String[] keys = zSections.keySet().toArray(new String[0]);
      Arrays.sort(keys);
      for (String key : keys) {
        int oldCount = zSections.get(key).intValue();
        if (oldCount == 1 && !key.replaceAll("\\d", "").equals("")) {
          oldFile += oldCount;
          continue;
        }
        int sizeC = isTiff ? tiff.getEffectiveSizeC() : getSizeC();
        int nPlanes = sizeC * getSizeT();
        int count = (int) Math.min(oldCount, nPlanes);
        System.arraycopy(tmpFiles, oldFile, files, nextFile, count);
        nextFile += count;
        oldFile += count;
        if (count < oldCount) oldFile += (oldCount - count);
      }
      core[0].imageCount = getSizeZ() * getEffectiveSizeC() * getSizeT();
    }
  }

  private void parseTimFile(String timFile) throws IOException {
    String[] tokens = DataTools.readFile(timFile).split("\\s");
    int tNum = 0;
    // can ignore "Zero x" and "Extra int"
    String[] hashKeys = {"Number of Wavelengths/Timepoints", "Zero 1",
      "Zero 2", "Number of slices", "Extra int", "Calibration Unit",
      "Pixel Size Y", "Pixel Size X", "Image Width", "Image Length",
      "Origin X", "SubfileType X", "Dimension Label X", "Origin Y",
      "SubfileType Y", "Dimension Label Y", "Origin Z",
      "SubfileType Z", "Dimension Label Z"};

    // there are 9 additional tokens, but I don't know what they're for

    for (String token : tokens) {
      if (token.trim().length() == 0) continue;
      if (tNum >= hashKeys.length) break;
      if (token.equals("um")) tNum = 5;
      while ((tNum == 1 || tNum == 2) && !token.trim().equals("0")) {
        tNum++;
      }
      if (tNum == 4) {
        try {
          Integer.parseInt(token);
        }
        catch (NumberFormatException e) {
          tNum++;
        }
      }
      parseKeyValue(hashKeys[tNum++], token);
    }
  }

  private void parseCSVFile(String csvFile) throws IOException {
    if (getMetadataOptions().getMetadataLevel() == MetadataLevel.MINIMUM) {
      return;
    }
    String[] tokens = DataTools.readFile(csvFile).split("\\s");
    Vector<String> tmp = new Vector<String>();
    for (String token : tokens) {
      if (token.trim().length() > 0) tmp.add(token.trim());
    }
    tokens = tmp.toArray(new String[0]);

    int tNum = 0;
    String[] hashKeys = {"Calibration Unit", "Pixel Size X", "Pixel Size Y",
      "Z slice space"};
    int pt = 0;
    for (int j=0; j<tokens.length;) {
      String key = null, value = null;
      if (tNum < 7) { j++; }
      else if ((tNum > 7 && tNum < 12) ||
        (tNum > 12 && tNum < 18) || (tNum > 18 && tNum < 22))
      {
        j++;
      }
      else if (pt < hashKeys.length) {
        key = hashKeys[pt++];
        value = tokens[j++];
      }
      else {
        key = tokens[j++] + tokens[j++];
        value = tokens[j++];
      }

      parseKeyValue(key, value);
      tNum++;
    }
  }

  private void parseZpoFile(String zpoFile) throws IOException {
    if (getMetadataOptions().getMetadataLevel() == MetadataLevel.MINIMUM) {
      return;
    }
    String[] tokens = DataTools.readFile(zpoFile).split("\\s");
    for (int t=0; t<tokens.length; t++) {
      addGlobalMeta("Z slice #" + t + " position", tokens[t]);
    }
  }

  // -- Helper class --

  class PEComparator implements Comparator<String> {
    public int compare(String s1, String s2) {
      if (s1.equals(s2)) return 0;

      int underscore1 = (int) Math.max(s1.lastIndexOf("_"), 0);
      int underscore2 = (int) Math.max(s2.lastIndexOf("_"), 0);
      int dot1 = (int) Math.max(s1.lastIndexOf("."), 0);
      int dot2 = (int) Math.max(s2.lastIndexOf("."), 0);

      String prefix1 = s1.substring(0, underscore1);
      String prefix2 = s2.substring(0, underscore2);

      if (!prefix1.equals(prefix2)) return prefix1.compareTo(prefix2);

      try {
        int z1 = Integer.parseInt(s1.substring(underscore1 + 1, dot1));
        int z2 = Integer.parseInt(s2.substring(underscore2 + 1, dot2));

        if (z1 < z2) return -1;
        if (z2 < z1) return 1;
      }
      catch (NumberFormatException e) { }

      try {
        int ext1 = Integer.parseInt(s1.substring(dot1 + 1), 16);
        int ext2 = Integer.parseInt(s2.substring(dot2 + 1), 16);

        if (ext1 < ext2) return -1;
        if (ext1 > ext2) return 1;
      }
      catch (NumberFormatException e) { }
      return 0;
    }
  }

}
