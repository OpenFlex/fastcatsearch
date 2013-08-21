package org.fastcatsearch.util;

import java.io.IOException;
import java.io.Writer;

public class JSONPResultWriter extends JSONResultWriter {
	public String callback;
	private Writer w;

	public JSONPResultWriter(Writer w, String callback) {
		this(w, callback, false);
	}

	public JSONPResultWriter(Writer w, String callback, boolean beautify) {
		super(w, beautify);
		this.w = w;
		this.callback = callback;
		try {
			w.write(callback);
			w.write("(");
			if (super.isBeautify()) {
				w.write("\r\n");
			}
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	public void done() {
		try {
			if (super.isBeautify()) {
				w.write("\r\n");
			}
			w.write(")");
		} catch (IOException e) {
			logger.error("", e);
		}
	}
}
