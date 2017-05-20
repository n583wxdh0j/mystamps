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
package ru.mystamps.web.controller.interceptor;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpMethod;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import lombok.RequiredArgsConstructor;

// TODO: javadoc
public class DownloadImageInterceptor extends HandlerInterceptorAdapter {
	
	public static final String ERROR_MESSAGE_ATTR_NAME = "DownloadedImage.ErrorMessage";
	
	private static final Logger LOG = LoggerFactory.getLogger(DownloadImageInterceptor.class);
	
	@Override
	@SuppressWarnings("PMD.SignatureDeclareThrowsException")
	public boolean preHandle(
		HttpServletRequest request,
		HttpServletResponse response,
		Object handler) throws Exception {
		
		if (!HttpMethod.POST.matches(request.getMethod())) {
			return true;
		}
		
		// Inspecting AddSeriesForm.imageUrl field.
		// If it doesn't have a value, then nothing to do here.
		String imageUrl = request.getParameter("imageUrl");
		if (StringUtils.isEmpty(imageUrl)) {
			return true;
		}
		
		if (!(request instanceof StandardMultipartHttpServletRequest)) {
			LOG.warn(
				"Unknown type of request ({}). "
				+ "Downloading images from external servers won't work!",
				request
			);
			return true;
		}
		
		LOG.debug("preHandle imageUrl = {}", imageUrl);
		
		StandardMultipartHttpServletRequest multipartRequest =
			(StandardMultipartHttpServletRequest)request;
		MultipartFile image = multipartRequest.getFile("image");
		if (image != null && StringUtils.isNotEmpty(image.getOriginalFilename())) {
			LOG.debug("User provided image, exited");
			// user specified both image and image URL, we'll handle it later, during validation
			return true;
		}
		
		// user specified image URL: we should download file and represent it as "downloadedImage" field.
		// Doing this our validation will be able to check downloaded file later.
		
		byte[] data;
		String contentType;
		try {
			URL url = new URL(imageUrl);
			LOG.debug("URL.getPath(): {} / URL.getFile(): {}", url.getPath(), url.getFile());
			
			if (!"http".equals(url.getProtocol())) {
				// TODO(security): fix possible log injection
				LOG.info("Invalid link '{}': only HTTP protocol is supported", imageUrl);
				return true;
			}
			
			try {
				URLConnection connection = url.openConnection();
				if (!(connection instanceof HttpURLConnection)) {
					LOG.warn(
						"Unknown type of connection class ({}). "
						+ "Downloading images from external servers won't work!",
						connection
					);
					return true;
				}
				HttpURLConnection conn = (HttpURLConnection)connection;
				
				conn.setRequestProperty(
					"User-Agent",
					"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:46.0) Gecko/20100101 Firefox/46.0"
				);
				
				long timeout = TimeUnit.SECONDS.toMillis(1);
				conn.setReadTimeout(Math.toIntExact(timeout));
				LOG.debug("getReadTimeout(): {}", conn.getReadTimeout());
				
				// TODO: how bad is it?
				conn.setInstanceFollowRedirects(false);
				
				try {
					conn.connect();
				} catch (IOException ex) {
					// TODO(security): fix possible log injection
					LOG.error("Couldn't connect to '{}': {}", imageUrl, ex.getMessage());
					setErrorMessage(request, "Could not connect to host");
					return true;
				}
				
				try (InputStream stream = new BufferedInputStream(conn.getInputStream())) {
					int status = conn.getResponseCode();
					if (status != HttpURLConnection.HTTP_OK) {
						// TODO(security): fix possible log injection
						LOG.error(
							"Couldn't download file '{}': bad response status {}",
							imageUrl,
							status
						);
						setErrorMessage(request, "Invalid response code from the server. Ensure that server doesn't do a redirect");
						return true;
					}
					
					contentType = conn.getContentType();
					LOG.debug("Content-Type: {}", contentType);
					if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
						// TODO(security): fix possible log injection
						LOG.error(
							"Couldn't download file '{}': unsupported image type '{}'",
							imageUrl,
							contentType
						);
						setErrorMessage(request, "Invalid file type. Only JPEG and PNG are supported");
						return true;
					}
					
					// TODO: content length can be -1 for gzipped responses
					// TODO: add protection against huge files
					int contentLength = conn.getContentLength();
					LOG.debug("Content-Length: {}", contentLength);
					if (contentLength <= 0) {
						// TODO(security): fix possible log injection
						LOG.error(
							"Couldn't download file '{}': it has {} bytes length",
							imageUrl,
							contentLength
						);
						setErrorMessage(request, "Invalid file size");
						return true;
					}
					
					data = StreamUtils.copyToByteArray(stream);
					
				} catch (FileNotFoundException ignored) {
					// TODO(security): fix possible log injection
					LOG.error("Couldn't download file '{}': not found", imageUrl);
					setErrorMessage(request, "File not found on the server");
					return true;
				}
		
			} catch (IOException ex) {
				// TODO(security): fix possible log injection
				LOG.error(
					"Couldn't download file from URL '{}': {}",
					imageUrl,
					ex.getMessage()
				);
				setErrorMessage(request, "Could not download file");
				return true;
			}
			
		} catch (MalformedURLException ex) {
			// TODO(security): fix possible log injection
			LOG.error("Invalid image URL '{}': {}", imageUrl, ex.getMessage());
			setErrorMessage(request, "Invalid URL");
			return true;
		}
		
		// TODO: use URL.getFile() instead of full link?
		multipartRequest.getMultiFileMap().set("downloadedImage", new MyMultipartFile(data, contentType, imageUrl));
		
		// TODO: how we can validate url?
		
		return true;
	}
	
	private static void setErrorMessage(HttpServletRequest request, String errorMessage) {
		request.setAttribute(ERROR_MESSAGE_ATTR_NAME, errorMessage);
	}
	
	@RequiredArgsConstructor
	private static class MyMultipartFile implements MultipartFile {
		private final byte[] content;
		private final String contentType;
		private final String link;
		
		@Override
		public String getName() {
			throw new IllegalStateException("Not implemented");
		}

		@Override
		public String getOriginalFilename() {
			return link;
		}

		@Override
		public String getContentType() {
			return contentType;
		}

		@Override
		public boolean isEmpty() {
			return getSize() == 0;
		}

		@Override
		public long getSize() {
			return content.length;
		}

		@Override
		public byte[] getBytes() throws IOException {
			return content;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return new ByteArrayInputStream(content);
		}

		@Override
		public void transferTo(File dest) throws IOException, IllegalStateException {
			throw new IllegalStateException("Not implemented");
		}
	}
	
}
