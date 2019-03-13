package filesystem;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Descriptor implements Serializable {

	private static final long serialVersionUID = -5794044325030819668L;

	private int len;
	private int[] diskMap;
	
	public Descriptor()
	{
		this.len = -1;
		this.diskMap = new int[3];
		Arrays.fill(this.diskMap, -1);
	}

	public Descriptor(int len, int[] diskMap) {
		this.len = len;
		this.diskMap = diskMap;
	}


	public void createNewDescriptor()
	{
		this.len = 0;
	}
	
	public int getLen() {
		return len;
	}

	public void updateLen(int len)
	{
		this.len += len;
	}

	public void setLen(int len) {
		this.len = len;
	}


	public int nextFreeBlock()
	{
		for(int i = 0; i < this.diskMap.length; i++)
		{
			if(this.diskMap[i] == -1)
				return i;
		}
		return -1; // all 3 disk blocks for file are allocated
	}



	public int getBlockIndex(int diskMapIndex)
	{
		return this.diskMap[diskMapIndex];
	}
	
	public int[] getDiskMap() {
		return diskMap;
	}

	public void setDiskMap(int[] blocks) {
		this.diskMap = blocks;
	}

	public void clearDiskMap()
	{
		for(int i = 0; i < this.diskMap.length; i++ )
			this.diskMap[i] = -1;
	}



	public void assignBlockToDescriptor(int diskMapIndex, int blockIndex ) {
		// diskMapIndex: 0, 1, 2    	-- refers to the nth element in a descriptors 3-size array that stores block indexes
		// blockIndex: 0, ..., 63	-- refers to the block index in the ldisk
		this.diskMap[diskMapIndex] = blockIndex;
	}

	public void loadFromByteArray(byte[] bA)
	{
		ByteBuffer b = ByteBuffer.wrap(bA);
		this.len = b.getInt();
		for(int i = 0; i < this.diskMap.length; i++)
		{
			this.diskMap[i] = b.getInt();
		}
	}
	
	public byte[] toByteArray()
	{
		byte[] ret = new byte[16];
		int offset = 0;
		FileSystem.copyArray(FileSystem.intToByteArray(this.len), 0, ret, offset);
		offset += 4;
		for(int i = 0; i < this.diskMap.length; i++)
		{
			FileSystem.copyArray(FileSystem.intToByteArray(this.diskMap[i]), 0, ret, offset);
			offset += 4;
		}		
		return ret;
	}


	@Override
	public String toString() {
		return "len: " + this.len + "  diskMap: " + Arrays.toString(this.diskMap);
	}

}
