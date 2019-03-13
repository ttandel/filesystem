package filesystem;

import java.io.Serializable;
import java.util.Arrays;

public class LDisk implements Serializable {

	private static final long serialVersionUID = -1131322256288904562L;

	private byte[][] ldisk;



	public LDisk()
	{
		this.ldisk = new byte[FileSystem.NUM_BLOCKS][FileSystem.BLOCK_LENGTH];
	}

	public LDisk(byte[][] ldisk) {
		this.ldisk = ldisk;
	}

	public byte[] readBlock(int index, byte[] p)
	{
		//p = this.ldisk[index];  // doesn't change p
		FileSystem.copyArray(this.ldisk[index], 0, p, 0);
		return this.ldisk[index];
	}
	
	public byte[] writeBlock(int index, byte[] p)
	{
		// this.ldisk[index] = p;
		FileSystem.copyArray(p, 0, this.ldisk[index], 0);
		return this.ldisk[index];
	}
	
	
	public String blockToString(int index)
	{
		String ret = "";
		for(byte bA: this.ldisk[index])
		{
			ret += FileSystem.byteToBinaryString(bA) + " ";
		}
		return ret;
	}
	
	
	public String bitmapToString()
	{
		String ret = "";
		for(int i = 0; i < FileSystem.NUM_BLOCKS / 8; i++)  // 8 = bits in bytes; (NUM_BLOCKS / 8) = # of bytes for bitmap
		{
			ret += FileSystem.byteToBinaryString(this.ldisk[0][i]) + " ";
		}
				
		return ret;
	}
	
	public String diskToString()
	{
		String ret = "";
		for(byte[] bA: this.ldisk)
		{
			ret += Arrays.toString(bA) + " ";
		}
		return ret;
	}
		
	
}
