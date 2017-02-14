package ru.mystamps.web;

public final class Db {
	
	public static final class Category {
		public static final int NAME_LENGTH = 50;
	}
	
	public static final class Country {
		public static final int NAME_LENGTH = 50;
	}
	
	public static final class Series {
		public static final int COMMENT_LENGTH = 255;
	}
	
	public static final class SuspiciousActivity {
		public static final int PAGE_URL_LENGTH     = 100;
		public static final int METHOD_LENGTH       = 7;
		public static final int REFERER_PAGE_LENGTH = 255;
		public static final int USER_AGENT_LENGTH   = 255;
	}
	
	public static final class UsersActivation {
		public static final int ACTIVATION_KEY_LENGTH = 10;
		public static final int EMAIL_LENGTH          = 255;
	}
	
	public static final class User {
		public static final int LOGIN_LENGTH = 15;
		public static final int NAME_LENGTH  = 100;
	}
	
}
