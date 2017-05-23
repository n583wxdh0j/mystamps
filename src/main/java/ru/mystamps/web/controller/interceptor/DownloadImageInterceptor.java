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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import lombok.RequiredArgsConstructor;

import ru.mystamps.web.service.DownloaderService;
import ru.mystamps.web.service.dto.DownloadResult;

/**
 * Converts image URL to an image by downloading it from a server and binding to a form field.
 *
 * It handles only POST requests.
 */
@RequiredArgsConstructor
public class DownloadImageInterceptor extends HandlerInterceptorAdapter {
	
	/**
	 * Field name that contains image URL.
	 */
	public static final String URL_PARAMETER_NAME   = "imageUrl";
	
	/**
	 * Field name to which downloaded image will be bound.
	 */
	public static final String IMAGE_FIELD_NAME     = "downloadedImage";
	
	/**
	 * Name of request attribute, that will be used for storing an error code.
	 *
	 * To check whether error has occurred, you can retrieve this attribute in a controller.
	 * When it's not {@code null}, it has the code in the format of fully-qualified name
	 * of the members of the {@link DownloadResult} enum.
	 */
	public static final String ERROR_CODE_ATTR_NAME = "DownloadedImage.ErrorCode";
	
	private static final Logger LOG = LoggerFactory.getLogger(DownloadImageInterceptor.class);
	
	private final DownloaderService downloaderService;
	
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
		String imageUrl = request.getParameter(URL_PARAMETER_NAME);
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
		
		StandardMultipartHttpServletRequest multipartRequest =
			(StandardMultipartHttpServletRequest)request;
		MultipartFile image = multipartRequest.getFile("image");
		if (image != null && StringUtils.isNotEmpty(image.getOriginalFilename())) {
			LOG.debug("User provided image, exited");
			// user specified both image and image URL, we'll handle it later, during validation
			return true;
		}
		
		// user specified image URL: we should download file and represent it as a field.
		// Doing this our validation will be able to check downloaded file later.
		DownloadResult result = downloaderService.download(imageUrl);
		if (result.hasFailed()) {
			setErrorMessage(request, result.getCode());
			return true;
		}
		
		MultipartFile downloadedImage =
			new ByteArrayMultipartFile(result.getData(), result.getContentType(), imageUrl);
		
		multipartRequest.getMultiFileMap().set(IMAGE_FIELD_NAME, downloadedImage);
		
		return true;
	}
	
	private static void setErrorMessage(HttpServletRequest request, DownloadResult.Code errorCode) {
		String msgCode = DownloadResult.class.getName() + "." + errorCode.toString();
		request.setAttribute(ERROR_CODE_ATTR_NAME, msgCode);
	}
	
}
