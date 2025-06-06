/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2020, 2021, 2022, 2024  AO Industries, Inc.
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

package com.aoapps.encoding.taglib.legacy;

import static javax.servlet.jsp.tagext.IterationTag.EVAL_BODY_AGAIN;
import static javax.servlet.jsp.tagext.Tag.EVAL_PAGE;
import static javax.servlet.jsp.tagext.Tag.SKIP_BODY;
import static javax.servlet.jsp.tagext.Tag.SKIP_PAGE;

import com.aoapps.lang.i18n.Resources;
import com.aoapps.servlet.jsp.LocalizedJspTagException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTag;

/**
 * Helper utilities for working with {@link BodyTag}.
 *
 * @author  AO Industries, Inc.
 */
@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
final class BodyTagUtils  {

  /** Make no instances. */
  private BodyTagUtils() {
    throw new AssertionError();
  }

  private static final Logger logger = Logger.getLogger(BodyTagUtils.class.getName());

  private static final Resources RESOURCES = Resources.getResources(ResourceBundle::getBundle, BodyTagUtils.class);

  static int checkAfterBodyReturn(int afterBodyReturn) throws JspTagException {
    if (
        afterBodyReturn != SKIP_BODY
            && afterBodyReturn != EVAL_BODY_AGAIN
    ) {
      throw new LocalizedJspTagException(RESOURCES, "checkAfterBodyReturn.invalid", afterBodyReturn);
    }
    return afterBodyReturn;
  }

  static int checkEndTagReturn(int endTagReturn) throws JspTagException {
    if (
        endTagReturn != EVAL_PAGE
            && endTagReturn != SKIP_PAGE
    ) {
      throw new LocalizedJspTagException(RESOURCES, "checkEndTagReturn.invalid", endTagReturn);
    }
    return endTagReturn;
  }

  private static final String BODY_CONTENT_IMPL_CLASS = "org.apache.jasper.runtime.BodyContentImpl";
  private static final String WRITER_FIELD = "writer";

  private static final Class<?> bodyContentImplClass;
  private static final Field writerField;

  static {
    Class<?> clazz;
    Field field;
    try {
      clazz = Class.forName(BODY_CONTENT_IMPL_CLASS);
      field = clazz.getDeclaredField(WRITER_FIELD);
      field.setAccessible(true);
    } catch (ThreadDeath td) {
      throw td;
    } catch (Throwable t) {
      if (logger.isLoggable(Level.INFO)) {
        logger.log(
            Level.INFO,
            "Cannot get direct access to the " + BODY_CONTENT_IMPL_CLASS + "." + WRITER_FIELD + " field.  "
                + "Unbuffering of BodyContent disabled.  "
                + "The system will behave correctly, but some optimizations are disabled.",
            t
        );
      }
      clazz = null;
      field = null;
    }
    bodyContentImplClass = clazz;
    writerField = field;
  }

  /**
   * Unbuffers a {@link BodyContent}, when possible.
   *
   * <p>This implementation is Tomcat-specific, in that it sets the <code>BodyContentImpl.writer</code> field directly
   * through reflection.</p>
   *
   * <p>TODO: Consider putting this Tomcat-specific optimization into a different package that would register itself
   * here.  Then this package could be selectively added to dependencies to allow the feature to only be enable in
   * development mode.</p>
   */
  static boolean unbuffer(BodyContent bodyContent, Writer writer) throws JspTagException {
    // Note: bodyContentImplClass will be null when direct access disabled
    if (bodyContentImplClass != null) {
      Class<? extends Writer> bodyContentClass = bodyContent.getClass();
      if (bodyContentClass == bodyContentImplClass) {
        try {
          assert writerField.get(bodyContent) == null : "writer must be null since is setup for buffering";
          writerField.set(bodyContent, writer);
          if (logger.isLoggable(Level.FINER)) {
            logger.finer("Successfully unbuffered BodyContextImpl");
          }
          return true;
        } catch (IllegalAccessException e) {
          if (logger.isLoggable(Level.SEVERE)) {
            logger.severe("Failed to unbuffer BodyContextImpl");
          }
          throw new JspTagException(e);
        }
      }
    }
    return false;
  }
}
