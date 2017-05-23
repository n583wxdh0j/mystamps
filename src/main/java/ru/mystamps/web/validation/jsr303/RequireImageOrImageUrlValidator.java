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
package ru.mystamps.web.validation.jsr303;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Implementation of {@link RequireImageOrImageUrl} validator.
 * If image and image URL fields are empty, it marks both fields as having an error.
 */
public class RequireImageOrImageUrlValidator
	implements ConstraintValidator<RequireImageOrImageUrl, HasImageOrImageUrl> {
	
	@Override
	public void initialize(RequireImageOrImageUrl annotation) {
		// Intentionally empty: nothing to initialize
	}
	
	@Override
	public boolean isValid(HasImageOrImageUrl value, ConstraintValidatorContext ctx) {
		
		if (value == null) {
			return true;
		}
		
		if (value.hasImage()) {
			return true;
		}
		
		if (value.hasImageUrl()) {
			return true;
		}

		// mark both fields as having an error
		// CheckStyle: ignore LineLength for next 1 line
		ConstraintViolationUtils.recreate(ctx, "imageUrl", ctx.getDefaultConstraintMessageTemplate());
		ConstraintViolationUtils.recreate(ctx, "image", ctx.getDefaultConstraintMessageTemplate());
		
		return false;
	}
	
}

