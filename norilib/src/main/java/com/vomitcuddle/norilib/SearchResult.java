package com.vomitcuddle.norilib;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Iterator;

public class SearchResult implements Parcelable {
  /** Class loader used when deserializing from {@link android.os.Parcel}. */
  public static final Parcelable.Creator<SearchResult> CREATOR = new Parcelable.Creator<SearchResult>() {
    @Override
    public SearchResult createFromParcel(Parcel source) {
      return new SearchResult(source);
    }

    @Override
    public SearchResult[] newArray(int size) {
      return new SearchResult[size];
    }
  };
  /** List of images */
  public ArrayList<Image> images = new ArrayList<Image>();
  /** Image count, across all pages */
  public long count;
  /** Offset, used for paging */
  public long offset;
  /** Current page number */
  public int pageNumber = 0;
  /** Query used to get this SearchResult */
  public String query;
  /** True if more results are available on the next page */
  private boolean hasMore = true;

  /** Default constructor */
  public SearchResult() {
  }

  /**
   * Constructor used for deserializing from {@link android.os.Parcel}.
   *
   * @param in {@link android.os.Parcel} to read values from.
   */
  protected SearchResult(Parcel in) {
    // Read values from Parcel.
    in.readList(images, Image.class.getClassLoader());
    count = in.readLong();
    pageNumber = in.readInt();
    offset = in.readLong();
    query = in.readString();
    hasMore = in.readByte() == 0x01;
  }

  /**
   * Extends the result with images from another page.
   *
   * @param result Results from another page.
   */
  public void extend(SearchResult result) {
    if (result.images.size() > 0) {
      // Merge array lists.
      images.addAll(result.images);
      // Set offset and increment page number.
      offset = result.offset;
      pageNumber++;
    } else {
      // Don't bother fetching next page.
      hasMore = false;
    }
  }

  /**
   * Extends the result with images from another page while removing any images above given {@link com.vomitcuddle.norilib.Image.ObscenityRating}.
   *
   * @param result Results from another page.
   * @param rating Most explicit acceptable rating.
   * @see #filter(com.vomitcuddle.norilib.Image.ObscenityRating)
   */
  public void extend(SearchResult result, Image.ObscenityRating rating) {
    boolean resultNotEmpty = result.images.size() > 0;
    result.filter(rating);
    extend(result);
    // Handle edge case where all images were above the explicit rating.
    if (resultNotEmpty)
      hasMore = true;
  }

  /**
   * Check if more results could be available on the next page.
   *
   * @return True if last call to {@link #extend(SearchResult)} added images to the result.
   */
  public boolean hasMore() {
    return hasMore;
  }

  /**
   * Remove images with {@link com.vomitcuddle.norilib.Image.ObscenityRating} above given rating.
   *
   * @param rating Most explicit acceptable rating.
   */
  public void filter(Image.ObscenityRating rating) {
    for (Iterator<Image> it = images.iterator(); it.hasNext(); ) {
      Image image = it.next();
      if ((image.obscenityRating == Image.ObscenityRating.QUESTIONABLE) && rating == Image.ObscenityRating.SAFE)
        it.remove();
      else if ((image.obscenityRating == Image.ObscenityRating.EXPLICIT)
          && (rating == Image.ObscenityRating.SAFE || rating == Image.ObscenityRating.QUESTIONABLE))
        it.remove();
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    // Write values to parcel.
    dest.writeList(images);
    dest.writeLong(count);
    dest.writeInt(pageNumber);
    dest.writeLong(offset);
    dest.writeString(query);
    dest.writeByte((byte) (hasMore ? 0x01 : 0x00));
  }
}
