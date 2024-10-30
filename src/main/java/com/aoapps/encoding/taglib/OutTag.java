/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2013, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2023, 2024  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-encoding-taglib.
 *
 * ao-encoding-taglib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-encoding-taglib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-encoding-taglib.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoapps.encoding.taglib;

import com.aoapps.encoding.MediaType;
import com.aoapps.hodgepodge.i18n.BundleLookupMarkup;
import com.aoapps.hodgepodge.i18n.BundleLookupThreadContext;
import com.aoapps.hodgepodge.i18n.MarkupType;
import com.aoapps.lang.Coercion;
import com.aoapps.lang.io.Writable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.servlet.jsp.JspException;
import org.w3c.dom.Node;

/**
 * @author  AO Industries, Inc.
 */
public class OutTag extends EncodingNullTag {

  public OutTag() {
    init();
  }

  @Override
  public MediaType getOutputType() {
    return mediaType;
  }

  /* BodyTag only:
    private static final long serialVersionUID = 1L;
  /**/

  private Object value;

  public void setValue(Object value) {
    this.value = value;
  }

  private ValueExpression def;
  private boolean defValueSet;
  private Object defValue;

  public void setDefault(ValueExpression def) {
    this.def = def;
    this.defValueSet = false;
    this.defValue = null;
  }

  private Object getDefault() {
    /* BodyTag only:
        ELContext elContext = pageContext.getELContext();
    /**/
    /* SimpleTag only: */
    ELContext elContext = getJspContext().getELContext();
    /**/
    if (def == null) {
      return null;
    }
    if (defValueSet) {
      return defValue;
    }
    Object myValue = def.getValue(elContext);
    defValue = myValue;
    defValueSet = true;
    return myValue;
  }

  /**
   * TODO: Support a type of "auto" (not the default - use with care) that
   * takes the media type from the object being written, if it implements
   * an interface that has a <code>getOutputType()</code> method.
   * If no <code>getOutputType()</code> method in auto mode, default to
   * {@link MediaType#TEXT} or throw exception?  Other objects with
   * a <code>getOutputType()</code> method should also implement this new
   * method?  Other places with <code>setType(Object)</code> might support
   * the same auto-mode.  Or would we allow a object passed into "type"
   * attribute to implement this interface?  Or a different "typeOf" attribute?
   */
  private MediaType mediaType;

  public void setType(Object type) {
    type = Coercion.trimNullIfEmpty(type);
    String typeStr = (type == null) ? null : Coercion.toString(type);
    MediaType newMediaType = MediaType.getMediaTypeByName(typeStr);
    if (newMediaType == null) {
      try {
        newMediaType = MediaType.getMediaTypeForContentType(typeStr);
      } catch (UnsupportedEncodingException e) {
        throw new IllegalArgumentException(e);
      }
    }
    this.mediaType = newMediaType;
  }

  private MarkupType markupType;
  private String toStringResult;
  private BundleLookupMarkup lookupMarkup;

  private void init() {
    value = null;
    def = null;
    defValueSet = false;
    defValue = null;
    mediaType = MediaType.TEXT;
    markupType = null;
    toStringResult = null;
    lookupMarkup = null;
  }

  @Override
  protected void writePrefix(MediaType containerType, Writer out) throws JspException, IOException {
    Object effectiveValue = (value != null) ? value : getDefault();
    if (effectiveValue != null) {
      markupType = containerType.getMarkupType();
      BundleLookupThreadContext threadContext;
      if (
          markupType != null
              && markupType != MarkupType.NONE
              && (threadContext = BundleLookupThreadContext.getThreadContext()) != null
              // Avoid intermediate String from Writable
              && (
              !(effectiveValue instanceof Writable)
                  || ((Writable) effectiveValue).isFastToString()
          )
              // Other types that will not be converted to String for bundle lookups
              && !(value instanceof char[])
              && !(value instanceof Node)
      ) {
        toStringResult = Coercion.toString(effectiveValue);
        // Look for any message markup
        lookupMarkup = threadContext.getLookupMarkup(toStringResult);
        if (lookupMarkup != null) {
          lookupMarkup.appendPrefixTo(markupType, out);
        }
      }
    }
  }

  @Override
  /* BodyTag only:
    protected int doEndTag(Writer out) throws JspException, IOException {
  /**/
  /* SimpleTag only: */
  protected void doTag(Writer out) throws JspException, IOException {
    /**/
    if (toStringResult != null) {
      out.write(toStringResult);
    } else if (value != null) {
      Coercion.write(value, out, true);
    } else {
      Object myDefault = getDefault();
      if (myDefault != null) {
        Coercion.write(myDefault, out, true);
      }
    }
    /* BodyTag only:
      return EVAL_PAGE;
  /**/
  }

  @Override
  protected void writeSuffix(MediaType containerType, Writer out) throws JspException, IOException {
    if (lookupMarkup != null) {
      lookupMarkup.appendSuffixTo(markupType, out);
    }
  }

  /* BodyTag only:
  @Override
  public void doFinally() {
    try {
      init();
    } finally {
      super.doFinally();
    }
  }
/**/
}
