package filesystem;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;

public class DiskHeader implements Serializable {
	private static final long serialVersionUID = 5038365766101218479L;

	private BitSet BM;
	private Descriptor[] descriptors;


	public DiskHeader(BitSet BM, Descriptor[] descriptors) {
		this.BM = BM;
		this.descriptors = descriptors;
	}

	public DiskHeader()
	{
		this.BM = new BitSet(FileSystem.NUM_BLOCKS);
		this.descriptors = new Descriptor[24];  // 6 blocks for descriptors = 24 descriptors (1 descriptor = 16 bytes, 1 block = 64 bytes)

		this.BM.set(0, 7); // disk blocks 0, 1, 2, 3, 4, 5, 6 reserved for bitmap and descriptors
		
		for(int i = 0; i < this.descriptors.length; i++)
		{
			this.descriptors[i] = new Descriptor();
		}
		this.descriptors[0].createNewDescriptor(); // initialize directory descriptor
		this.descriptors[0].assignBlockToDescriptor(0,7); // allocate 7th disk block for directory data
	}

	public Descriptor getDescriptor(int descriptorIndex)
	{
		return this.descriptors[descriptorIndex];
	}

	public void setBit(int diskBlockIndex)
	{
		this.BM.set(diskBlockIndex);
	}

	public void setBit(int fromIndex, int toIndex)
	{
		this.BM.set(fromIndex, toIndex);
	}

	public void clearBit(int diskBlockIndex)
	{
		this.BM.clear(diskBlockIndex);
	}

	public void clearBit(int fromIndex, int toIndex)
	{
		this.BM.clear(fromIndex, toIndex);
	}

	public int getNextFreeDataBlockIndex()
	{
		// NUM_DESCRIPTOR_BLOCKS = 6;  next clear bit from 8 (inclusive)
		// start with 8 as 0-6 reserved for bitmap & header and the first data block after the last descriptor block
		// will always be the first data block of directory
		int returnVal = this.BM.nextClearBit(FileSystem.NUM_DESCRIPTOR_BLOCKS + 2);
		if (returnVal >= FileSystem.BLOCK_LENGTH)
			// bitset grows to 128 bits if all 64 bits are set when nextClearBit is called
			// but the bitmap should only have 64 bits so return -1 to indicate all disk blocks are occupied
			return -1;
		return returnVal;
	}

	public int getNextFreeDescriptorIndex()
	{
		for(int i = 1; i < this.descriptors.length; i++) // start from descriptor 1 as descriptor 0 is reserved for directory
		{
			if(this.descriptors[i].getLen() == -1)
				return i;
		}
		return -1; // -1: no free descriptor found
	}
	
	public byte[] bitmapToDiskBlock()
	{	
		return Arrays.copyOf(this.BM.toByteArray(), FileSystem.BLOCK_LENGTH); // 64 block size
	}

	public Descriptor[] getDescriptors() {
		return descriptors;
	}

	public Descriptor[] getDescriptorBlock(int descBlockIndex)
	{
		Descriptor[] ret = new Descriptor[4]; // 1 disk block has 4 descriptors
		int fromIndex = (0 + descBlockIndex) * 4;
		int toIndex = fromIndex + 4;
		for(int i = 0, j = fromIndex; j < toIndex; i++, j++)
			ret[i] = this.descriptors[j];
		return ret;
	}

	public byte[] descriptorBlockToByteArray(int descBlockIndex)
	// descBlock: [0, 5]; getDescriptorBlock(0) would return descriptors 0,1,2,3 as
	// a byte array
	{
		byte[] bA = new byte[FileSystem.BLOCK_LENGTH];
		int fromIndex = (0 + descBlockIndex) * 4;
		int toIndex = fromIndex + 4;
		for(int i = fromIndex, offset = 0; i < toIndex; i++, offset+=16)
		{
			FileSystem.copyArray(this.descriptors[i].toByteArray(), 0, bA, offset);
		}
		return bA;
	}

	public void diskBlockToDescriptors(int descBlockIndex, byte[] block)
	{
		int fromIndex = (0 + descBlockIndex) * 4;
		int toIndex = fromIndex + 4;
		for(int i = fromIndex, offset = 0; i < toIndex; i++, offset+=16)
		{
			this.descriptors[i].loadFromByteArray(Arrays.copyOfRange(block, offset, offset + 16)); // 16: size of descriptor in bytes
		}
	}


	public void printDescriptors()
	{
		for(int i =0; i < this.descriptors.length; i++)
		{
			if(i % 4 == 0)
				System.out.println("---------------------------------------------------------");
			System.out.println("descriptor " + i + ": " + this.descriptors[i]);
		}
	}

	public void printDescriptors(int toIndex)
	{
		for(int i = 0; i < toIndex; i++)
		{
			if(i % 4 == 0)
				System.out.println("---------------------------------------------------------");
			System.out.println("descriptor " + i + ": " + this.descriptors[i]);
		}
	}

	public void printDescriptors(int fromIndex, int toIndex)
	{
		for(int i = fromIndex; i < toIndex; i++)
		{
			if(i % 4 == 0)
				System.out.println("---------------------------------------------------------");
			System.out.println("descriptor " + i + ": " + this.descriptors[i]);
		}
	}

	
	
	
}
