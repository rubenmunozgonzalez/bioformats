package ome.scifio;

import java.io.File;
import java.io.IOException;

import ome.scifio.io.RandomAccessInputStream;

/**
 * Interface for all SciFIO Readers.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="">Trac</a>,
 * <a href="">Gitweb</a></dd></dl>
 */
public interface Reader<M extends Metadata> extends MetadataHandler<M> {

  // -- Reader API methods --

  /**
   * Obtains the specified image plane from the current file as a byte array.
   * @see #openBytes(int, byte[])
   */
  byte[] openBytes(int iNo, int no) throws FormatException, IOException;

  /**
   * Obtains a sub-image of the specified image plane,
   * whose upper-left corner is given by (x, y).
   */
  byte[] openBytes(int iNo, int no, int x, int y, int w, int h)
    throws FormatException, IOException;

  /**
   * Obtains the specified image plane from the current file into a
   * pre-allocated byte array of
   * (sizeX * sizeY * bytesPerPixel * RGB channel count).
   *
   * @param iNo the image index within the file.
   * @param no the plane index within the image.
   * @param buf a pre-allocated buffer.
   * @return the pre-allocated buffer <code>buf</code> for convenience.
   * @throws FormatException if there was a problem parsing the metadata of the
   *   file.
   * @throws IOException if there was a problem reading the file.
   */
  byte[] openBytes(int iNo, int no, byte[] buf) throws FormatException, IOException;

  /**
   * Obtains a sub-image of the specified image plane
   * into a pre-allocated byte array.
   *
   * @param iNo the image index within the file.
   * @param no the plane index within the image.
   * @param buf a pre-allocated buffer.
   * @param dims a map of dimension labels (e.g., "x", "y") to the size of the
   *             corresponding dimension (e.g., sizeX, sizeY) 
   * @return the pre-allocated buffer <code>buf</code> for convenience.
   * @throws FormatException if there was a problem parsing the metadata of the
   *   file.
   * @throws IOException if there was a problem reading the file.
   */
  byte[] openBytes(int iNo, int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException;

  /**
   * Obtains the specified image plane (or sub-image thereof) in the reader's
   * native data structure. For most readers this is a byte array; however,
   * some readers call external APIs that work with other types such as
   * {@link java.awt.image.BufferedImage}. The openPlane method exists to
   * maintain generality and efficiency while avoiding pollution of the API
   * with AWT-specific logic.
   *
   * @see ome.scifio.FormatReader
   * @see ome.scifio.in.BufferedImageReader
   */
  Object openPlane(int iNo, int no, int x, int y, int w, int h)
    throws FormatException, IOException;

  /**
   * Obtains a thumbnail for the specified image plane from the current file,
   * as a byte array.
   */
  byte[] openThumbBytes(int iNo, int no) throws FormatException, IOException;

  /** Specifies whether or not to force grouping in multi-file formats. */
  void setGroupFiles(boolean group);

  /** Returns true if we should group files in multi-file formats.*/
  boolean isGroupFiles();

  /**
   * Returns an int indicating that we cannot, must, or might group the files
   * in a given dataset.
   */
  int fileGroupOption(String id) throws FormatException, IOException;

  /** Returns the current file. */
  String getCurrentFile();

  /** Returns the list of domains represented by the current file. */
  String[] getDomains();

  /**
   * Gets the rasterized index corresponding
   * to the given Z, C and T coordinates.
   */
  int getIndex(int z, int c, int t);

  /**
   * Gets the Z, C and T coordinates corresponding
   * to the given rasterized index value.
   */
  int[] getZCTCoords(int index);

  /**
   * Sets the default input stream for this reader.
   * 
   * @param stream a RandomAccessInputStream for the source being read
   */
  void setStream(RandomAccessInputStream stream);

  /**
   * Retrieves the current input stream for this reader.
   * @return A RandomAccessInputStream
   */
  RandomAccessInputStream getStream();

  /**
   * Retrieves all underlying readers.
   * Returns null if there are no underlying readers.
   */
  Reader<Metadata>[] getUnderlyingReaders();

  /** Returns the optimal sub-image width for use with openBytes. */
  int getOptimalTileWidth();

  /** Returns the optimal sub-image height for use with openBytes. */
  int getOptimalTileHeight();

  /** Sets the Metadata for this Reader */
  public void setMetadata(M meta);

  /** Gets the Metadata for this Reader */
  public M getMetadata();
  
  //TODO remove normalization methods
  /** Specifies whether or not to normalize float data. */
  void setNormalized(boolean normalize);

  /** Returns true if we should normalize float data. */
  boolean isNormalized();

  /**
   * Sets the source for this reader to read from.
   * @param file
   * @throws IOException 
   */
  public void setSource(File file) throws IOException;

  /**
   * Sets the source for this reader to read from.
   * @param fileName
   * @throws IOException 
   */
  public void setSource(String fileName) throws IOException;

  /**
   * Sets the source for this reader to read from.
   * @param in
   */
  public void setSource(RandomAccessInputStream stream);
  
  /**
   * Closes the currently open file. If the flag is set, this is all that
   * happens; if unset, it is equivalent to calling
   * {@link IFormatHandler#close()}.
   */
  void close(boolean fileOnly) throws IOException;
  
  /** Closes currently open file(s) and frees allocated memory. */
  public void close() throws IOException;
}
