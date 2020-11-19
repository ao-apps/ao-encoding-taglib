/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2012, 2016, 2017, 2019, 2020  AO Industries, Inc.
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
 * along with ao-encoding-taglib.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.encoding.taglib;

import com.aoindustries.io.LocalizedIOException;
import static com.aoindustries.encoding.taglib.ApplicationResources.accessor;
import java.io.IOException;
import java.io.Writer;

// TODO: Move to aocode-public (and eventually a separate ao-io/ao-io-utils)?
final class FailOnWriteWriter extends Writer {

	private static final FailOnWriteWriter instance = new FailOnWriteWriter();

	static FailOnWriteWriter getInstance() {
		return instance;
	}

	private FailOnWriteWriter() {
	}

	@Override
	public void write(int c) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public void write(char cbuf[]) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public void write(String str) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public void write(String str, int off, int len) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public FailOnWriteWriter append(CharSequence csq) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public FailOnWriteWriter append(CharSequence csq, int start, int end) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public FailOnWriteWriter append(char c) throws IOException {
		throw new LocalizedIOException(accessor, "FailOnWriteWriter.noOutputAllowed");
	}

	@Override
	public void flush() {
		// Do nothing
	}

	@Override
	public void close() {
		// Do nothing
	}
}
