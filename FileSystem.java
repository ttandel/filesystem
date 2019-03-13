package filesystem;

import java.io.*;

public class FileSystem implements Serializable {


	private static final long serialVersionUID = -714735507960425530L;

	public static final int BLOCK_LENGTH = 64;
	public static final int NUM_BLOCKS = 64;
	public static final int INT_SIZE = 4; // int size: 4 bytes
	public static final int OFT_SIZE = 4;
	public static final int DISK_MAP_SIZE = 3;
	public static final int DIRECTORY_SLOT_SIZE = 8; // slot size in bytes; 4 bytes for file name, 4 bytes for descriptor index
	public static final int FILE_NAME_SIZE = 4;
	public static final int MAX_FILE_LEN = BLOCK_LENGTH * DISK_MAP_SIZE; // = 192 Bytes
	public static final int NUM_DIRECTORY_SLOTS = MAX_FILE_LEN / DIRECTORY_SLOT_SIZE; //192 / 8 = 24 entries/descriptors
	public static final int NUM_DESCRIPTOR_BLOCKS = 6;
	public static final int NUM_RESERVED_BLOCKS = NUM_DESCRIPTOR_BLOCKS + 1; // + 1 for bitmap


	// FileSystem member variables
	private LDisk disk;
	private DiskHeader header;
	private OFTEntry[] OFT;


	class OFTEntry implements Serializable {
		private static final long serialVersionUID = -425381669106789641L;

		private byte[] rwBuffer;
		private int currentPos;
		private int descriptorIndex; // descriptor index in disk header
		private int len; // file length in bytes

		OFTEntry() {
			this.rwBuffer = new byte[FileSystem.BLOCK_LENGTH];
			this.currentPos = 0;
			this.descriptorIndex = -1;
			this.len = -1;
		}

		public OFTEntry(byte[] rwBuffer, int currentPos, int descriptorIndex, int len) {
			this.rwBuffer = rwBuffer;
			this.currentPos = currentPos;
			this.descriptorIndex = descriptorIndex;
			this.len = len;
		}

		void modifyEntry(int dIndex, int fileLength, byte[] dataBlock) {
			this.rwBuffer = dataBlock;
			this.currentPos = 0;
			this.descriptorIndex = dIndex;
			this.len = fileLength;
		}
	}


	// FileSystem methods

	public FileSystem() {
		this.initializeFileSystem();
	}

	public FileSystem(LDisk disk, DiskHeader header, OFTEntry[] OFT) {
		this.disk = disk;
		this.header = header;
		this.OFT = OFT;
	}

	private void initializeFileSystem()
	{
		this.disk = new LDisk();
		this.header = new DiskHeader();
		this.OFT = new OFTEntry[FileSystem.OFT_SIZE];

		Descriptor directoryDescriptor = this.header.getDescriptor(0); // descriptor 0 is always the directory descriptor
		byte[] dataBlock = new byte[BLOCK_LENGTH];
		this.disk.readBlock(directoryDescriptor.getBlockIndex(0), dataBlock); // read first directory data block

		this.initializeOFT();

		this.openDirectory(0, directoryDescriptor.getLen(), dataBlock); // dIndex: 0 = directory descriptor
	}

	public String create(String fileName)
	{
		int status = this.createFile(fileName);
		if (status < 0)
			return "error";
		return fileName + " created";
	}

	public String destroy(String fileName)
	{
		int status = this.destroyFile(fileName);
		if (status < 0)
			return "error";
		return fileName + " destroyed";
	}

	public String open(String fileName)
	{
		int status = this.openFile(fileName);
		if (status < 0)
			return "error";
		return fileName + " opened " + status;
	}

	public String close(int index)
	{
		int status = this.closeFile(index);
		if (status < 0)
			return "error";
		return index + " closed";
	}

	public String read(int index, byte[] mem_area, int count)
	{
		int status = this.readFileInEntry(index, mem_area, count);
		if (status < 0)
			return "error";
		return new String(mem_area).trim();
	}

	public String write(int index, byte[] mem_area, int count)
	{
		int status = this.writeFileInEntry(index, mem_area, count);
		if (status < 0)
			return "error";
		return status + " bytes written";
	}

	public String lseek(int index, int pos)
	{
		int status = this.seekToPosInEntry(index, pos);
		if (status < 0)
			return "error";
		return "position is " + status;
	}

	public String directory()
	{
		return this.listFilesInDirectory();
	}

	public String initialize(String fileName)
	{
		int status = this.restoreDiskFromFile(fileName);
		if(status == 0)
			return "disk initialized";
		else if (status == 1)
			return "disk restored";
		else
			return "error";
	}

	public String save(String fileName)
	{
		int status = this.saveDiskToFile(fileName);
		if (status < 0)
			return "error";
		return  "disk saved";
	}


	static public byte[] copyArray(byte[] src, int srcOffset, byte[] dest, int destOffset) {
		for (int i = srcOffset; i < src.length; ++i) {
			if (destOffset >= dest.length)
				return dest;
			dest[destOffset++] = src[i];
		}
		return dest;
	}


	static public String byteToBinaryString(byte b) {
		return Integer.toBinaryString(b & 255 | 256).substring(1);
	}

	static public String byteArrayToString(byte[] bA) {
		String ret = "";
		for (byte b : bA) {
			ret += byteToBinaryString(b) + " ";
		}
		return ret;
	}

	// OFT methods

	private void initializeOFT() {
		this.OFT = new OFTEntry[FileSystem.OFT_SIZE];
		for (int i = 0; i < this.OFT.length; i++)
			this.OFT[i] = new OFTEntry();
	}

	private void openDirectory(int dIndex, int fileLength, byte[] dataBlock) {
		// not using dIndex because directory descriptor should always be descriptor 0
		// left dIndex in, in case the above criteria changes
		this.OFT[0].modifyEntry(0, fileLength, dataBlock);
	}

	private int findFileInDirectory(String fileName) {
		// directory slot is 8 bytes: 4 bytes for file name, 4 bytes for file descriptor index
		byte[] directorySlot = new byte[FileSystem.DIRECTORY_SLOT_SIZE];

		this.seekToPosInEntry(0, 0);


		for (int i = 0; i < this.OFT[0].len; i += FileSystem.DIRECTORY_SLOT_SIZE) {
			byte[] nameInBytes = new byte[FileSystem.FILE_NAME_SIZE];

			this.readFileInEntry(0, directorySlot, FileSystem.DIRECTORY_SLOT_SIZE);

			FileSystem.copyArray(directorySlot, 0, nameInBytes, 0);
			nameInBytes[FILE_NAME_SIZE - 1] = (byte) 0;

			String dsFileName = new String(nameInBytes).trim();
			if (fileName.equals(dsFileName)) {
				return i;
			}
		}
		return -1;
	}

	private int getFreeDirectorySlotPos() {
		this.seekToPosInEntry(0, 0);

		if (this.OFT[0].len == 0)
			return 0;

		for (int i = 0; i <= this.OFT[0].len; i += FileSystem.DIRECTORY_SLOT_SIZE) {
			byte[] directorySlot = new byte[FileSystem.DIRECTORY_SLOT_SIZE];
			this.readFileInEntry(0, directorySlot, FileSystem.DIRECTORY_SLOT_SIZE);
			if (this.getDescriptorIndexFromDirectorySlot(directorySlot) <= 0) // descriptor index 0, -1 means directory slot is free
			{
				// subtract the length of currentPos as currentPos will be pointing to the beginning of next directory slot
				// after read
				return this.OFT[0].currentPos;
			}
		}
		return -1;

	}

	private int openFile(String fileName)
	// returns status of trying to open file
	// [1, 3]: file opened successfully
	// -1: error
	{
		// find file descriptor index
		byte[] dirSlot = new byte[FileSystem.DIRECTORY_SLOT_SIZE];
		int dirSlotPos = this.findFileInDirectory(fileName);

		if (dirSlotPos < 0)
			return -1;

		this.seekToPosInEntry(0, dirSlotPos);
		this.readFileInEntry(0, dirSlot, FileSystem.DIRECTORY_SLOT_SIZE);

		int descriptorIndex = this.getDescriptorIndexFromDirectorySlot(dirSlot);
		if (descriptorIndex <= 0)
			return -1;

		int fileLength = this.header.getDescriptor(descriptorIndex).getLen();
		int firstDataBlockIndex = this.header.getDescriptor(descriptorIndex).getBlockIndex(0);

		byte[] firstDataBlock = new byte[FileSystem.BLOCK_LENGTH];
		this.disk.readBlock(firstDataBlockIndex, firstDataBlock);

		// modify OFT
		int oftIndex = this.getFreeOftEntryIndex();
		if (oftIndex != -1) // if oft entry is free
			this.OFT[oftIndex].modifyEntry(descriptorIndex, fileLength, firstDataBlock);
		return oftIndex;
	}

	private int closeFile(int oftIndex) {
		if ((oftIndex < 0) || (oftIndex >= FileSystem.OFT_SIZE) || this.OFT[oftIndex].descriptorIndex < 0)
			return -1;

//		int diskMapIndex = this.OFT[oftIndex].currentPos / FileSystem.BLOCK_LENGTH;

		int diskMapIndex = 0;
		if (this.OFT[oftIndex].currentPos >= 64 && this.OFT[oftIndex].currentPos < 128)
			diskMapIndex = 1;
		if (this.OFT[oftIndex].currentPos >= 128 && this.OFT[oftIndex].currentPos <= 192)
			diskMapIndex = 2;

		int diskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(diskMapIndex);

		this.disk.writeBlock(diskBlockIndex, this.OFT[oftIndex].rwBuffer);
		// update file length in file descriptor
		this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).setLen(this.OFT[oftIndex].len);

		// free OFT entry
		if (oftIndex != 0) // if open file is not directory
		{
			this.OFT[oftIndex].currentPos = 0;
			this.OFT[oftIndex].descriptorIndex = -1;
			this.OFT[oftIndex].len = -1;
		}
		return 0;
	}

	private int getFreeOftEntryIndex() {
		for (int i = 1; i < this.OFT.length; i++) // oft entry 0 reserved for directory
		{
			if (this.OFT[i].descriptorIndex == -1)
				return i;
		}
		return -1;
	}

	private int getDescriptorIndexFromDirectorySlot(byte[] directorySlot) {
		return FileSystem.byteArrayToInt(
				FileSystem.copyArray(directorySlot, FileSystem.FILE_NAME_SIZE,
						new byte[FileSystem.INT_SIZE], 0));
	}

//	private void setDescriptorIndexInDirectorySlot(int dIndex, byte[] directorySlot)
//	{
//		FileSystem.copyArray(FileSystem.intToByteArray(dIndex), 0,
//				directorySlot, FileSystem.FILE_NAME_SIZE);
//	}

	private String getFileNameFromDirectorySlot(byte[] directorySlot) {
		byte[] fileNameInBytes = new byte[FileSystem.FILE_NAME_SIZE];
		FileSystem.copyArray(directorySlot, 0, fileNameInBytes, 0);
		fileNameInBytes[FileSystem.FILE_NAME_SIZE - 1] = (byte) 0;

		return new String(fileNameInBytes).trim();
	}

	private byte[] createDirectorySlot(String fileName, int descriptorIndex) {
		byte[] bA = new byte[FileSystem.DIRECTORY_SLOT_SIZE];
		byte[] fileNameInBytes = new byte[FileSystem.FILE_NAME_SIZE];
		FileSystem.copyArray(fileName.getBytes(), 0, fileNameInBytes, 0);
		fileNameInBytes[FileSystem.FILE_NAME_SIZE - 1] = (byte) 0;

		FileSystem.copyArray(fileNameInBytes, 0, bA, 0);
		FileSystem.copyArray(FileSystem.intToByteArray(descriptorIndex), 0, bA, FileSystem.FILE_NAME_SIZE);

		return bA;
	}


	private int createFile(String fileName) {
		byte[] newDirSlot = new byte[FileSystem.DIRECTORY_SLOT_SIZE];
		int descriptorIndex = this.header.getNextFreeDescriptorIndex();
		if (descriptorIndex < 0) // if there are no free file descriptors
			return -1;

		newDirSlot = this.createDirectorySlot(fileName, descriptorIndex);
		if (this.findFileInDirectory(fileName) >= 0) // file with name fileName exists
			return -1;

		int directorySlotPos = this.getFreeDirectorySlotPos();
		if (directorySlotPos < 0) // if there are no free directory slots
			return -1;

		this.seekToPosInEntry(0, directorySlotPos);

		int nextFreeDataBlock = this.header.getNextFreeDataBlockIndex();
		if (nextFreeDataBlock < 0) // if all the data blocks have been allocated / disk is full
			return -1;

		this.header.setBit(nextFreeDataBlock);
		this.header.getDescriptor(descriptorIndex).setLen(0);
		this.header.getDescriptor(descriptorIndex).assignBlockToDescriptor(0, nextFreeDataBlock);

		this.writeFileInEntry(0, newDirSlot, FileSystem.DIRECTORY_SLOT_SIZE);
		return 0;
	}

	private int getOftEntryIndexWithDescriptorIndex(int descriptorIndex) {
		for (int i = 0; i < this.OFT.length; i++) {
			if (this.OFT[i].descriptorIndex == descriptorIndex)
				return i;
		}
		return -1;
	}

	private int destroyFile(String fileName) {
		byte[] dirSlot = new byte[FileSystem.DIRECTORY_SLOT_SIZE];
		int dirSlotPos = this.findFileInDirectory(fileName);

		if (dirSlotPos < 0)
			return -1;

		this.seekToPosInEntry(0, dirSlotPos);
		this.readFileInEntry(0, dirSlot, FileSystem.DIRECTORY_SLOT_SIZE);

		int descriptorIndex = this.getDescriptorIndexFromDirectorySlot(dirSlot);

		// free directory slot
		dirSlot = this.createDirectorySlot("   ", -1);
		this.seekToPosInEntry(0, dirSlotPos);
		this.writeFileInEntry(0, dirSlot, FileSystem.DIRECTORY_SLOT_SIZE);

		// update bitmap
		if (descriptorIndex > 0) // if file descriptor is not empty and not directory descriptor
		{
			int[] diskMap = this.header.getDescriptor(descriptorIndex).getDiskMap();
			for (int blockIndex : diskMap) {
				if (blockIndex > FileSystem.NUM_RESERVED_BLOCKS - 1)
					this.header.clearBit(blockIndex);
			}
		}

		// close file if open
		int oftIndex = this.getOftEntryIndexWithDescriptorIndex(descriptorIndex);
		if (oftIndex != -1)
			this.closeFile(oftIndex);


		// free file descriptor
		this.header.getDescriptor(descriptorIndex).setLen(-1);
		this.header.getDescriptor(descriptorIndex).clearDiskMap();
		return 0;
	}

	private int readFileInEntry(int oftIndex, byte[] mem_area, int count) {
		if ((oftIndex < 0) || (oftIndex >= FileSystem.OFT_SIZE) || this.OFT[oftIndex].descriptorIndex < 0)
			return -1;

		int numBytesToRead = Math.min(count, this.OFT[oftIndex].len - this.OFT[oftIndex].currentPos);

		int rwBufferPos = this.OFT[oftIndex].currentPos % FileSystem.BLOCK_LENGTH;

		for (int i = 0; i < numBytesToRead; rwBufferPos++, this.OFT[oftIndex].currentPos++, i++) {
			if (rwBufferPos >= FileSystem.BLOCK_LENGTH) // if we have reached the end of rwBuffer
			{
				int nextDiskMapIndex = this.OFT[oftIndex].currentPos / FileSystem.BLOCK_LENGTH;
				int nextDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(
						nextDiskMapIndex);
				int prevDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(
						nextDiskMapIndex - 1);
				this.disk.writeBlock(prevDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
				this.disk.readBlock(nextDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
				rwBufferPos = this.OFT[oftIndex].currentPos % FileSystem.BLOCK_LENGTH; // re-calculate rw buffer pos

			}
			mem_area[i] = this.OFT[oftIndex].rwBuffer[rwBufferPos];
		}
		return numBytesToRead;
	}

	private int writeFileInEntry(int oftIndex, byte[] mem_area, int count) {
		if ((oftIndex < 0) || (oftIndex >= FileSystem.OFT_SIZE) || this.OFT[oftIndex].descriptorIndex < 0)
			return -1;

		int numBytesToWrite = Math.min(count, FileSystem.MAX_FILE_LEN - this.OFT[oftIndex].currentPos);

		int rwBufferPos = this.OFT[oftIndex].currentPos % FileSystem.BLOCK_LENGTH;

		for (int i = 0; i < numBytesToWrite; rwBufferPos++, this.OFT[oftIndex].currentPos++, i++) {
			if (rwBufferPos >= FileSystem.BLOCK_LENGTH) // end of buffer reached
			{
				int nextDiskMapIndex = 1;
				if (this.OFT[oftIndex].currentPos == 128)
					nextDiskMapIndex = 2;
				int prevDiskMapIndex = nextDiskMapIndex - 1;
				int prevDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(prevDiskMapIndex);
				int nextDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(
						nextDiskMapIndex);

				if (nextDiskBlockIndex < 0) {
					int nextFreeBlock = this.header.getNextFreeDataBlockIndex();
					if (nextFreeBlock < 0) // this means the disk has no free blocks to allocate
					{
						this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).setLen(numBytesToWrite);
						return i; // i = the # of bytes written at this point
					}
					this.header.setBit(nextFreeBlock);
					this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).assignBlockToDescriptor(nextDiskMapIndex,
							nextFreeBlock);
					nextDiskBlockIndex = nextFreeBlock;
				}
				this.disk.writeBlock(prevDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
				this.disk.readBlock(nextDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
				rwBufferPos = this.OFT[oftIndex].currentPos % FileSystem.BLOCK_LENGTH; // re-calculate rw buffer pos
			}
			this.OFT[oftIndex].rwBuffer[rwBufferPos] = mem_area[i];
		}

		if (this.OFT[oftIndex].currentPos == 64 || this.OFT[oftIndex].currentPos == 128)
		{
			int nextDiskMapIndex = 1;
			if (this.OFT[oftIndex].currentPos == 128)
				nextDiskMapIndex = 2;
			int prevDiskMapIndex = nextDiskMapIndex - 1;
			int prevDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(prevDiskMapIndex);
			int nextDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(
					nextDiskMapIndex);
			if (nextDiskBlockIndex < 0)
			{
				int nextFreeBlock = this.header.getNextFreeDataBlockIndex();
				if (nextFreeBlock < 0) // this means the disk has no free blocks to allocate
				{
					this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).setLen(numBytesToWrite);
					return numBytesToWrite;
				}
				this.header.setBit(nextFreeBlock);
				this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).assignBlockToDescriptor(nextDiskMapIndex,
						nextFreeBlock);
				nextDiskBlockIndex = nextFreeBlock;
			}
			this.disk.writeBlock(prevDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
			this.disk.readBlock(nextDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
		}


		this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).updateLen(numBytesToWrite);
		this.OFT[oftIndex].len = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getLen();
		return numBytesToWrite;
	}

	private int seekToPosInEntry(int oftIndex, int pos) {
		if ((oftIndex < 0) || (oftIndex >= FileSystem.OFT_SIZE) || this.OFT[oftIndex].descriptorIndex < 0 ||
				pos < 0 || pos > FileSystem.MAX_FILE_LEN || pos > this.OFT[oftIndex].len + 1)  // len of file is a valid pos
			// as long as file len is not 192
			return -1;

		int currentPos = this.OFT[oftIndex].currentPos;
		int currentDiskMapIndex = 0;
		if( currentPos >= 0 && currentPos < 64 )
			currentDiskMapIndex = 0;
		if( currentPos >= 64 && currentPos < 128 )
			currentDiskMapIndex = 1;
		if( currentPos >= 128 && currentPos < 192 )
			currentDiskMapIndex = 2;

		int goToIndex = 0;
		if( pos >= 0 && pos < 64 )
			goToIndex = 0;
		if( pos >= 64 && pos < 128 )
			goToIndex = 1;
		if( pos >= 128 && pos < 192 )
			goToIndex = 2;

		if(goToIndex == currentDiskMapIndex)
		{
			this.OFT[oftIndex].currentPos = pos;
			return pos;
		}
		else
		{
			int currentDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(
					currentDiskMapIndex);
			int goToDiskBlockIndex = this.header.getDescriptor(this.OFT[oftIndex].descriptorIndex).getBlockIndex(goToIndex);
			if(goToDiskBlockIndex < 0)
				return -1;
			this.disk.writeBlock(currentDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
			this.disk.readBlock(goToDiskBlockIndex, this.OFT[oftIndex].rwBuffer);
			this.OFT[oftIndex].currentPos = pos;
			return pos;
		}
	}

	private String listFilesInDirectory() {
		String fileNames = "";

		this.seekToPosInEntry(0, 0);

		for (int i = 0; i < this.OFT[0].len; i += FileSystem.DIRECTORY_SLOT_SIZE) {
			byte[] directorySlot = new byte[FileSystem.DIRECTORY_SLOT_SIZE];
			this.readFileInEntry(0, directorySlot, FileSystem.DIRECTORY_SLOT_SIZE);
			fileNames += this.getFileNameFromDirectorySlot(directorySlot) + " ";
		}
		return fileNames;
	}

	public int saveDiskToFile(String outputFileName)
	{
		// write disk map to disk
		this.disk.writeBlock(0,this.header.bitmapToDiskBlock());

		//close all open files
		for(int i = 0; i < this.OFT.length; i++)
		{
			if (this.OFT[i].len != -1)
				this.closeFile(i);
		}

		// write disk header to disk
		for(int i = 0; i < FileSystem.NUM_DESCRIPTOR_BLOCKS; i++)
		{
			this.disk.writeBlock(i + 1, this.header.descriptorBlockToByteArray(i));
		}
		try {
			ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(outputFileName));
			outputStream.writeObject(this);
			outputStream.close();
		} catch (IOException e) {
			return -1;
		}
		return 0;
	}

	public int restoreDiskFromFile(String inFileName)
			// return:	0	- disk initialized
			//			1	- disk restored
			//		   -1	- error
	{
		FileInputStream inputFile = null;
		int option = 0; // initialize disk by default
		try {
			inputFile = new FileInputStream(inFileName);
			option = 1; // restore
		} catch (FileNotFoundException e) {
			option = 0; // initialize disk
		}

		if(option == 0)
		{
			this.initializeFileSystem();
		}
		else if(option == 1) // restore disk
		{
			try {

				ObjectInputStream inputStream = new ObjectInputStream(inputFile);
				FileSystem f2 = (FileSystem) inputStream.readObject();
				this.header = f2.header;
				this.disk = f2.disk;
				this.OFT = this.OFT;
				byte[] dataBlock = new byte[FileSystem.BLOCK_LENGTH];
				this.disk.readBlock(this.header.getDescriptor(0).getBlockIndex(0), dataBlock);
				this.openDirectory(0,this.header.getDescriptor(0).getLen(), dataBlock);

				for(int i = 1; i < this.OFT.length; i++)
				{
					this.OFT[i].currentPos = 0;
					this.OFT[i].descriptorIndex = -1;
					this.OFT[i].len = -1;
				}

			} catch (IOException e) {
				return -1;
			} catch (ClassNotFoundException e) {
				return -1;
			}

		}
		return option;
	}



	static public byte[] intToByteArray(int val) {
		byte[] bA = new byte[INT_SIZE];
		final int MASK = 0xff;
		for (int i = 3; i >= 0; i--) {
			bA[i] = (byte) (val & MASK);
			val = val >> 8;
		}
		return bA;
	}

	static public int byteArrayToInt(byte[] bA) {
		final int MASK = 0xff;
		int v = (int) bA[0] & MASK;
		for (int i = 1; i < 4; i++) {
			v = v << 8;
			v = v | ((int) bA[i] & MASK);
		}
		return v;
	}


	public void printDisk()
	{
		System.out.println(this.disk.diskToString());
	}
}
