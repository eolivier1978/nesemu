package nesemu.util;

public class Convert
{
	public static byte getValueFromHexChar(char hex_char)
	{
		switch (hex_char)
		{
			case '0' : return 0x0;
			case '1' : return 0x1;
			case '2' : return 0x2;
			case '3' : return 0x3;
			case '4' : return 0x4;
			case '5' : return 0x5;
			case '6' : return 0x6;
			case '7' : return 0x7;
			case '8' : return 0x8;
			case '9' : return 0x9;
			case 'a' : 
			case 'A' : return 0xA;
			case 'b' :
			case 'B' : return 0xB;
			case 'c' :
			case 'C' : return 0xC;
			case 'd' :
			case 'D' : return 0xD;
			case 'e' :
			case 'E' : return 0xE;
			case 'f' :
			case 'F' : return 0xF;
		}
		
		return 0;
	}
	
	public static char getHexCharFromValue(byte value)
	{
		switch (value)
		{
			case 0x0 : return '0';
			case 0x1 : return '1';
			case 0x2 : return '2';
			case 0x3 : return '3';
			case 0x4 : return '4';
			case 0x5 : return '5';
			case 0x6 : return '6';
			case 0x7 : return '7';
			case 0x8 : return '8';
			case 0x9 : return '9';
			case 0xA : return 'A';
			case 0xB : return 'B';
			case 0xC : return 'C';
			case 0xD : return 'D';
			case 0xE : return 'E';
			case 0xF : return 'F';
		}
		
		return 0;
	}
	
	public static byte getByteFromHexString(String hex)
	{
		char first_char = hex.charAt(0);
		char second_char = hex.charAt(1);
		
		byte first_char_value = getValueFromHexChar(first_char);
		byte second_char_value = getValueFromHexChar(second_char);
		
		int value = (first_char_value << 4) + second_char_value;
		byte byte_value = (byte)(value & 0xFF);
		
		return byte_value;
	}
	
	public static String getHexStringFromUnsigned16BitInt(int the_int)
	{
		byte high_byte = (byte)((the_int & 0xFF00) >> 8);
		byte low_byte = (byte)(the_int & 0x00FF);
		
		return getHexStringFromByte(high_byte)+getHexStringFromByte(low_byte);
	}
	
	public static String getHexStringFromByte(byte the_byte)
	{
		int temp = the_byte;
		int high_nibble = (temp & 0xF0) >> 4;
		int low_nibble = temp & 0x0F;
		
		char high_char = getHexCharFromValue((byte)high_nibble);
		char low_char = getHexCharFromValue((byte)low_nibble);
		
		char[] char_array = new char[2];
		char_array[0] = high_char;
		char_array[1] = low_char;
		
		return new String(char_array);
	}
	
	public static int getUnsignedByte(byte the_byte)
	{
		return ((int)the_byte) & 0xFF;
	}

}
