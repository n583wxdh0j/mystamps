/*
 * Copyright (C) 2009-2017 Slava Semushin <slava.semushin@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package ru.mystamps.web.service;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.StreamUtils;

import ru.mystamps.web.service.dto.DownloadResult;
import ru.mystamps.web.service.dto.DownloadResult.Code;

public class HttpURLConnectionDownloaderService implements DownloaderService {
	
	private static final Logger LOG =
		LoggerFactory.getLogger(HttpURLConnectionDownloaderService.class);
	
	// we don't support redirects because it allows to bypass some of our validations
	// (for example, for a protocol)
	@SuppressWarnings({"PMD.RedundantFieldInitializer", "PMD.ImmutableField"})
	private boolean followRedirects = false;
	
	@Override
	public DownloadResult download(String fileUrl) {
		// TODO(security): fix possible log injection
		LOG.debug("Downloading {}", fileUrl);
		
		try {
			URL url = new URL(fileUrl);
			
			Code validationResult = validateUrl(url);
			if (validationResult != Code.SUCCESS) {
				return DownloadResult.failed(validationResult);
			}
			
			HttpURLConnection conn = openConnection(url);
			if (conn == null) {
				return DownloadResult.failed(Code.UNEXPECTED_ERROR);
			}
			
			configureUserAgent(conn);
			configureTimeouts(conn);
			configureRedirects(conn);
			
			Code connectionResult = connect(conn);
			if (connectionResult != Code.SUCCESS) {
				return DownloadResult.failed(Code.COULD_NOT_CONNECT);
			}
			
			try (InputStream stream = new BufferedInputStream(conn.getInputStream())) {
				validationResult = validateConnection(conn);
				if (validationResult != Code.SUCCESS) {
					return DownloadResult.failed(validationResult);
				}
				
				byte[] data = StreamUtils.copyToByteArray(stream);
				String contentType = conn.getContentType();
				return DownloadResult.succeeded(data, contentType);
				
			} catch (FileNotFoundException ignored) {
				LOG.debug("Couldn't download file: not found on the server");
				return DownloadResult.failed(Code.FILE_NOT_FOUND);
			}
			
		} catch (MalformedURLException ex) {
			LOG.error("Couldn't download file: invalid URL: {}", ex.getMessage());
			return DownloadResult.failed(Code.INVALID_URL);

		} catch (IOException ex) {
			LOG.warn("Couldn't download file", ex);
			return DownloadResult.failed(Code.UNEXPECTED_ERROR);
		}
		
	}
	
	private static Code validateUrl(URL url) {
		// TODO: make it configurable
		if ("http".equals(url.getProtocol())) {
			return Code.SUCCESS;
		}
		
		LOG.debug("Couldn't download file: invalid protocol. Only HTTP protocol is supported");
		return Code.INVALID_PROTOCOL;
	}
	
	private static HttpURLConnection openConnection(URL url) throws IOException {
		URLConnection connection = url.openConnection();
		if (!(connection instanceof HttpURLConnection)) {
			LOG.warn(
				"Couldn't open connection: "
				+ "unknown type of connection class ({}). "
				+ "Downloading images from external servers won't work!",
				connection
			);
			return null;
		}

		return (HttpURLConnection)connection;
	}
	
	private static void configureUserAgent(URLConnection conn) {
		// TODO: make it configurable
		conn.setRequestProperty(
			"User-Agent",
			"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:46.0) Gecko/20100101 Firefox/46.0"
		);
	}
	
	private static void configureTimeouts(URLConnection conn) {
		// TODO: make it configurable
		int timeout = Math.toIntExact(TimeUnit.SECONDS.toMillis(1));
		conn.setConnectTimeout(timeout);
		conn.setReadTimeout(timeout);
	}
	
	private void configureRedirects(HttpURLConnection conn) {
		conn.setInstanceFollowRedirects(followRedirects);
	}
	
	private static Code connect(HttpURLConnection conn) {
		try {
			conn.connect();
			return Code.SUCCESS;
			
		} catch (IOException ex) {
			LOG.debug(
				"Couldn't download file: connect has failed with error '{}'",
				ex.getMessage()
			);
			return Code.COULD_NOT_CONNECT;
		}
	}
	
	private Code validateConnection(HttpURLConnection conn) throws IOException {
		int status = conn.getResponseCode();
		if (status == HttpURLConnection.HTTP_MOVED_TEMP
			|| status == HttpURLConnection.HTTP_MOVED_PERM) {
			if (!followRedirects) {
				LOG.debug("Couldn't download file: redirects are disallowed");
				return Code.INVALID_REDIRECT;
			}
			
		} else if (status != HttpURLConnection.HTTP_OK) {
			LOG.debug("Couldn't download file: bad response status {}", status);
			return Code.INVALID_RESPONSE_CODE;
		}
		
		String contentType = conn.getContentType();
		if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
			LOG.debug("Couldn't download file: unsupported file type '{}'", contentType);
			return Code.INVALID_FILE_TYPE;
		}
		
		// TODO: content length can be -1 for gzipped responses
		// TODO: add protection against huge files
		int contentLength = conn.getContentLength();
		if (contentLength <= 0) {
			LOG.debug("Couldn't download file: invalid Content-Length: {}", contentLength);
			return Code.INVALID_FILE_SIZE;
		}
		
		return Code.SUCCESS;
	}
	
}
