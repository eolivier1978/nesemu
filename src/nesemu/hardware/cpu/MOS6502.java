package nesemu.hardware.cpu;

import nesemu.hardware.bus.NESBus;

public class MOS6502
{
	private NESBus bus;
	
	// CPU Core registers, exposed as public here for ease of access from external classes.
	public /*unsigned 8bit*/  int a; 				// Accumulator Register
	public /*unsigned 8bit*/  int x; 				// X Register
	public /*unsigned 8bit*/  int y; 				// Y Register
	public /*unsigned 8bit*/  int stkp; 			// Stack Pointer (points to location on bus)
	public /*unsigned 16bit*/ int pc; 				// Program Counter
	public /*unsigned 8bit*/  int status; 			// Status register
	
	// Assisting variables to facilitate emulation.
	private /*unsigned 8bit*/  int  fetched;   		// Represents the working input value to the ALU.
	private /*unsigned 16bit*/ int  temp; 			// A convenience variable used everywhere.
	private /*unsigned 16bit*/ int  addr_abs; 		// All used memory addresses end up in here. High byte is page number, low byte is byte in page.
	private /*signed 8bit*/    byte addr_rel;   		// Represents absolute address following a branch.  High byte is page number, low byte is byte in page.
	private /*unsigned 8bit*/  int  opcode;   		// Is the instruction byte.
	private /*unsigned 8bit*/  int  cycles;	   		// Counts how many cycles the instruction has remaining.
	
	private Operation ADDRESS_MODE_ABS = new ABS();
	private Operation ADDRESS_MODE_ABX = new ABX();
	private Operation ADDRESS_MODE_ABY = new ABY();
	private Operation ADDRESS_MODE_IMM = new IMM();
	private Operation ADDRESS_MODE_IMP = new IMP();
	private Operation ADDRESS_MODE_IND = new IND();
	private Operation ADDRESS_MODE_IZX = new IZX();
	private Operation ADDRESS_MODE_IZY = new IZY();
	private Operation ADDRESS_MODE_REL = new REL();
	private Operation ADDRESS_MODE_ZP0 = new ZP0();
	private Operation ADDRESS_MODE_ZPX = new ZPX();
	private Operation ADDRESS_MODE_ZPY = new ZPY();

	private Operation OPCODE_ADC = new ADC();
	private Operation OPCODE_AND = new AND();
	private Operation OPCODE_ASL = new ASL();
	private Operation OPCODE_BCC = new BCC();
	private Operation OPCODE_BCS = new BCS();
	private Operation OPCODE_BEQ = new BEQ();
	private Operation OPCODE_BIT = new BIT();
	private Operation OPCODE_BMI = new BMI();
	private Operation OPCODE_BNE = new BNE();
	private Operation OPCODE_BPL = new BPL();
	private Operation OPCODE_BRK = new BRK();
	private Operation OPCODE_BVC = new BVC();
	private Operation OPCODE_BVS = new BVS();
	private Operation OPCODE_CLC = new CLC();
	private Operation OPCODE_CLD = new CLD();
	private Operation OPCODE_CLI = new CLI();
	private Operation OPCODE_CLV = new CLV();
	private Operation OPCODE_CMP = new CMP();
	private Operation OPCODE_CPX = new CPX();
	private Operation OPCODE_CPY = new CPY();
	private Operation OPCODE_DEC = new DEC();
	private Operation OPCODE_DEX = new DEX();
	private Operation OPCODE_DEY = new DEY();
	private Operation OPCODE_EOR = new EOR();
	private Operation OPCODE_INC = new INC();
	private Operation OPCODE_INX = new INX();
	private Operation OPCODE_INY = new INY();
	private Operation OPCODE_JMP = new JMP();
	private Operation OPCODE_JSR = new JSR();
	private Operation OPCODE_LDA = new LDA();
	private Operation OPCODE_LDX = new LDX();
	private Operation OPCODE_LDY = new LDY();
	private Operation OPCODE_LSR = new LSR();
	private Operation OPCODE_NOP = new NOP();
	private Operation OPCODE_ORA = new ORA();
	private Operation OPCODE_PHA = new PHA();
	private Operation OPCODE_PHP = new PHP();
	private Operation OPCODE_PLA = new PLA();
	private Operation OPCODE_PLP = new PLP();
	private Operation OPCODE_ROL = new ROL();
	private Operation OPCODE_ROR = new ROR();
	private Operation OPCODE_RTI = new RTI();
	private Operation OPCODE_RTS = new RTS();
	private Operation OPCODE_SBC = new SBC();
	private Operation OPCODE_SEC = new SEC();
	private Operation OPCODE_SED = new SED();
	private Operation OPCODE_SEI = new SEI();
	private Operation OPCODE_STA = new STA();
	private Operation OPCODE_STX = new STX();
	private Operation OPCODE_STY = new STY();
	private Operation OPCODE_TAX = new TAX();
	private Operation OPCODE_TAY = new TAY();
	private Operation OPCODE_TSX = new TSX();
	private Operation OPCODE_TXA = new TXA();
	private Operation OPCODE_TXS = new TXS();
	private Operation OPCODE_TYA = new TYA();
	private Operation OPCODE_XXX = new XXX();
	
	public Instruction[] lookup = new Instruction[256];
	
	private Instruction instruction;
	
	// The CPU in the NTSC version of the NES operates at a twelfth the speed of the main clock and at a third the speed of the
	// PPU.
	public static final double NTSC_FREQUENCY = NESBus.MASTER_NTSC_FREQUENCY / 12.0;
	
	public MOS6502()
	{
		initializeLookupTable();
	}
	
	public void ConnectBus(NESBus bus)
	{
		this.bus = bus;
	}
	
	private static interface Operation
	{
		public int Execute();
	}
	
	// Addressing Modes =============================================
	// The 6502 has a variety of addressing modes to access data in 
	// memory, some of which are direct and some are indirect (like
	// pointers in C++). Each opcode contains information about which
	// addressing mode should be employed to facilitate the 
	// instruction, in regards to where it reads/writes the data it
	// uses. The address mode changes the number of bytes that
	// makes up the full instruction, so we implement addressing
	// before executing the instruction, to make sure the program
	// counter is at the correct location, the instruction is
	// primed with the addresses it needs, and the number of clock
	// cycles the instruction requires is calculated. These functions
	// may adjust the number of cycles required depending upon where
	// and how the memory is accessed, so they return the required
	// adjustment.
	
	///////////////////////////////////////////////////////////////////////////////
	//ADDRESSING MODES
	
	//The 6502 can address between 0x0000 - 0xFFFF. The high byte is often referred
	//to as the "page", and the low byte is the offset into that page. This implies
	//there are 256 pages, each containing 256 bytes.
	//
	//Several addressing modes have the potential to require an additional clock
	//cycle if they cross a page boundary. This is combined with several instructions
	//that enable this additional clock cycle. So each addressing function returns
	//a flag saying it has potential, as does each instruction. If both instruction
	//and address function return 1, then an additional clock cycle is required.
	
	// Address Mode: Absolute 
	// A full 16-bit address is loaded and used.
	public class ABS implements Operation
	{
		public int Execute()
		{
			/*unsigned 8bit*/ int lo = read(pc);
			pc++;

			/*unsigned 8bit*/ int hi = read(pc);
			pc++;
			
			// Create an unsigned 16bit address using the low and high bytes.
			// NOTE: 16bit overflow cannot occur because the highest value you can get with the operation below is FFFF.
			addr_abs = (hi << 8) | lo;
			
			return 0;
		}
	};
	
	// Address Mode: Absolute with X Offset
	// Fundamentally the same as absolute addressing, but the contents of the X Register
	// is added to the supplied two byte address. If the resulting address changes
	// the page, an additional clock cycle is required
	public class ABX implements Operation 
	{
		public int Execute()
		{
			/*unsigned 8bit*/ int lo = read(pc);
			pc++;

			/*unsigned 8bit*/ int hi = read(pc);
			pc++;
	
			// Create an unsigned 16bit address using the low and high bytes.
			// NOTE: 16bit overflow cannot occur because the highest value you can get with the operation below is FFFF.
			addr_abs = (hi << 8) | lo;
			// Add the value of the X register. This may cause the page (i.e. the high byte) to change.
			addr_abs += x;
			// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
			// 16bit value.
			addr_abs &= 0xFFFF;
	
			// If the page changes, then an additional clock cycle must be waited for. This is checked
			// by comparing the original high byte before the offset with the high byte after the offset. If it changed, then
			// an additional clock cycle has to be added.
			if ((addr_abs & 0xFF00) != (hi << 8))
				return 1;
			else
				return 0;
		}
	};
	
	// Address Mode: Absolute with Y Offset
	// Fundamentally the same as absolute addressing, but the contents of the Y Register
	// is added to the supplied two byte address. If the resulting address changes
	// the page, an additional clock cycle is required.
	public class ABY implements Operation
	{
		public int Execute()
		{
			/*unsigned 8bit*/ int lo = read(pc);
			pc++;

			/*unsigned 8bit*/ int hi = read(pc);
			pc++;
	
			// Create an unsigned 16bit address using the low and high bytes.
			// NOTE: 16bit overflow cannot occur because the highest value you can get with the operation below is FFFF.
			addr_abs = (hi << 8) | lo;
			// Add the value of the Y register. This may cause the page (i.e. the high byte) to change.
			addr_abs += y;
			// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
			// 16bit value.
			addr_abs &= 0xFFFF;
	
			// If the page changes, then an additional clock cycle must be waited for. This is checked below by bitwise
			// ANDing the high byte of the resultant address in the addr_abs variable with the original high byte. If they differ
			// then we know a page changed.
			if ((addr_abs & 0xFF00) != (hi << 8))
				return 1;
			else
				return 0;
		}
	};
	
	// Address Mode: Immediate
	// The instruction expects the next byte to be used as a value, so we'll prepare
	// the read address to point to the next byte.
	public class IMM implements Operation 
	{	
		public int Execute()
		{
			addr_abs = pc;
			pc++;

			return 0;
		}
	};
	
	// Address Mode: Implied
	// There is no additional data required for this instruction. The instruction
	// does something very simple like like sets a status bit. However, we will
	// target the accumulator, for instructions like PHA.
	public class IMP implements Operation 
	{
		public int Execute()
		{
			fetched = a;
			return 0;
		}
	};
	
	// Note: The next 3 address modes use indirection.

	// Address Mode: Indirect
	// The supplied 16-bit address is read to get the actual 16-bit address. This
	// instruction is unusual in that it has a bug in the hardware! To emulate its
	// function accurately, we also need to emulate this bug. If the low byte of the
	// supplied address is 0xFF, then to read the high byte of the actual address
	// we need to cross a page boundary. This doesn't actually work on the chip as 
	// designed, instead it wraps back around in the same page, yielding an 
	// invalid actual address.
	public class IND implements Operation 
	{
		public int Execute()
		{
			// Read the low and high bytes that will be constituted into a 16bit pointer
			/*unsigned 8bit*/ int ptr_lo = read(pc);
			pc++;

			/*unsigned 8bit*/ int ptr_hi = read(pc);
			pc++;
	
			// Create an unsigned 16bit pointer using the low and high bytes.
			// NOTE: 16bit overflow cannot occur because the highest value you can get with the operation below is FFFF.
			/*unsigned 16bit*/ int ptr = (ptr_hi << 8) | ptr_lo;
	
			// The pointer variable above (ptr) points to another 16bit address in memory. This 16bit address is the one
			// that should be used for the instruction. The 16bit address being pointed to is composed of 2 bytes so these
			// 2 bytes should be read one after the other to compose the actual 16bit address.
			// There are 2 cases:
			// 1) The pointer can point to a memory address where the 2 bytes that should be read are in the same page.
			// 2) The pointer can point to a memory address where the 2 bytes are in different pages i.e. the high byte is in
			//    the next page. This will be the case when the low byte of the pointer is FF, because adding 1 to FF will result
			//    in a carry of 1 into the high byte, meaning the page changed. For example, if the pointer pointed to an
			//    address of 05FF, the 2 bytes will be at 05FF and 0600 respectively. Note that the 05 changed to a 06 in this
			//    example, meaning the page changed. There is a bug in the NES however that must be emulated: The NES does not
			//    honour the carry of 1 to change the page, it just ignores it, so in the example above the 2 bytes will be 
			//    at 05FF and 0500. Note that the page did not change, it stayed 05. NES game developers worked around this bug
			//    and games rely on it, so we have to emulate the bug as well. The bug is emulated by not incrementing by 1 to
			//    read the second (i.e. the high) byte of the 16bit address, but rather reading this byte at the address in the same
			//    page as the low byte and at offset 00 in this page.
			if (ptr_lo == 0x00FF) // Simulate page boundary hardware bug
			{
				// Read the first byte at the normal position, but the second byte needs to be read in the same page at offset 00.
				// This is done by bitwise ANDing the pointer with FF00 which keeps the high byte of the address (i.e. the page)
				// but sets the low byte (i.e. the offset) to 00.
				// NOTE: 16bit overflow cannot occur because the highest value you can get with the operation below is FFFF.
				addr_abs = (read(ptr & 0xFF00) << 8) | read(ptr + 0);
			}
			else // Behave normally
			{
				// Read 2 consecutive bytes from the address that is now in the ptr variable and create a new address from
				// these 2 bytes, using the first byte as the low byte and the second byte as the high byte.
				// Even though only 1 is added to the pointer to read the second byte, we should guard against overflow of
				// the unsigned 16 bit value.
				int ptr_second_byte = ptr + 1;
				ptr_second_byte &= 0xFFFF;
				addr_abs = (read(ptr_second_byte) << 8) | read(ptr + 0);
			}
			
			return 0;
		}
	};
	
	// Address Mode: Indirect X
	// The supplied 8-bit address is offset by X Register to index
	// a location in page 0x00. The actual 16-bit address is read 
	// from this location.
	public class IZX implements Operation 
	{
		public int Execute()
		{
			// Read the 8bit pointer that is the offset into the 0th page.
			/*unsigned 8bit*/ int t = read(pc);
			pc++;
	
			// Add the X register to the address above. Read 2 consecutive bytes from this resultant address in the 0th page and 
			// create a new 16bit address which is the actual address for the instruction.

			// NOTE: An additional clock cycle will never be required by the page can never change, all reads are in page 0.
			/*unsigned 16bit*/ int lo = read((t + x) & 0x00FF);
			/*unsigned 16bit*/ int hi = read((t + x + 1) & 0x00FF);
	
			// NOTE: 16bit overflow cannot occur because the highest value you can get with the operation below is FFFF.
			addr_abs = (hi << 8) | lo;
			
			return 0;
		}
	};
	
	// Address Mode: Indirect Y
	// The supplied 8-bit address indexes a location in page 0x00. From 
	// here the actual 16-bit address is read, and the contents of
	// Y Register is added to it to offset it. If the offset causes a
	// change in page then an additional clock cycle is required.
	public class IZY implements Operation 
	{
		public int Execute()
		{
			// Read the 8bit pointer that is the offset into the 0th page.
			/*unsigned 8bit*/ int t = read(pc);
			pc++;
	
			// Read 2 consecutive bytes from the offset retrieved above in the 0th page.
			/*unsigned 16bit*/ int lo = read(t & 0x00FF);
			/*unsigned 16bit*/ int hi = read((t + 1) & 0x00FF);
	
			// Create a new 16bit address from these 2 bytes.
			// NOTE: 16bit overflow cannot occur because the highest value you can get with the operation below is FFFF.
			addr_abs = (hi << 8) | lo;
			// Offset it by the value in the Y register.
			// Adding the Y register to the address may cause a 16bit overflow, for example if addr_abs is FFFF already and
			// register Y can be anything, this will cause unsigned 16bit overflow. I am not sure what should happen in this
			// case. One would think that the developer of the NES game should not allow this to occur otherwise the game will
			// crash, but perhaps there are games that require overflow to occur here? Or maybe the remainder value should be used?
			addr_abs += y;
			// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
			// 16bit value.
			addr_abs &= 0xFFFF;
			
			// If the page changes due to the offset above, then an additional clock cycle must be waited for. This is checked
			// by comparing the original high byte before the offset with the high byte after the offset. If it changed, then
			// an additional clock cycle has to be added.
			if ((addr_abs & 0xFF00) != (hi << 8))
				return 1;
			else
				return 0;
		}
	};
	
	// Address Mode: Relative
	// This address mode is exclusive to branch instructions. The address
	// must reside within -128 to +127 of the branch instruction, i.e.
	// you can't directly branch to any address in the addressable range.
	public class REL implements Operation 
	{
		public int Execute()
		{
			// I made the addr_rel variably a normal Java signed byte, because that is how it's interpreted in the 6502. This
			// means that additions with an int will work normally i.e. if the signed byte is negative, it will be subtracted from
			// the int.
			addr_rel = (byte)read(pc);
			pc++;
	
			return 0;
		}
	};
	
	// Address Mode: Zero Page
	// To save program bytes, zero page addressing allows you to absolutely address
	// a location in first 0xFF bytes of address range. Clearly this only requires
	// one byte instead of the usual two.
	public class ZP0 implements Operation 
	{
		public int Execute()
		{
			addr_abs = read(pc);	
			pc++;

			// Clear the high byte which sets it to 0, we only want to operate in page 0 for this addressing mode.
			addr_abs &= 0x00FF;
			return 0;
		}
	};
	
	// Address Mode: Zero Page with X Offset
	// Fundamentally the same as Zero Page addressing, but the contents of the X Register
	// is added to the supplied single byte address. This is useful for iterating through
	// ranges within the first page.
	public class ZPX implements Operation 
	{
		public int Execute()
		{
			addr_abs = (read(pc) + x);
			// NOTE: Since the value returned from the read operation above is an unsigned 8bit value and the X register is also
			// 8bit, 16bit overflow cannot occur, so no need to check for it here.
			pc++;

			// Clear the high byte which sets it to 0, we only want to operate in page 0 for this addressing mode.
			addr_abs &= 0x00FF;
			return 0;
		}
	};
	
	// Address Mode: Zero Page with Y Offset
	// Same as above but uses Y Register for offset.
	public class ZPY implements Operation
	{
		public int Execute()
		{
			addr_abs = (read(pc) + y);
			pc++;

			// Clear the high byte which sets it to 0, we only want to operate in page 0 for this addressing mode.
			addr_abs &= 0x00FF;
			return 0;
		}
	};
	
	// Opcodes ======================================================
	// There are 56 "legitimate" opcodes provided by the 6502 CPU. I
	// have not modeled "unofficial" opcodes. As each opcode is 
	// defined by 1 byte, there are potentially 256 possible codes.
	// Codes are not used in a "switch case" style on a processor,
	// instead they are responsible for switching individual parts of
	// CPU circuits on and off. The opcodes listed here are official, 
	// meaning that the functionality of the chip when provided with
	// these codes is as the developers intended it to be. Unofficial
	// codes will of course also influence the CPU circuitry in 
	// interesting ways, and can be exploited to gain additional
	// functionality.
	//
	// These functions return 0 normally, but some are capable of
	// requiring more clock cycles when executed under certain
	// conditions combined with certain addressing modes. If that is 
	// the case, they return 1.
	
	///////////////////////////////////////////////////////////////////////////////
	//INSTRUCTION IMPLEMENTATIONS
	
	//Instruction: Add with Carry In
	//Function:    A = A + M + C
	//Flags Out:   C, V, N, Z
	//
	//Explanation:
	//The purpose of this function is to add a value to the accumulator and a carry bit. If
	//the result is > 255 there is an overflow setting the carry bit. This allows you to
	//chain together ADC instructions to add numbers larger than 8-bits. This in itself is
	//simple, however the 6502 supports the concepts of Negativity/Positivity and Signed Overflow.
	//
	//10000100 = 128 + 4 = 132 in normal circumstances, we know this as unsigned and it allows
	//us to represent numbers between 0 and 255 (given 8 bits). The 6502 can also interpret 
	//this word as something else if we assume those 8 bits represent the range -128 to +127,
	//i.e. it has become signed.
	//
	//Since 132 > 127, it effectively wraps around, through -128, to -124. This wrap around is
	//called overflow, and this is a useful to know as it indicates that the calculation has
	//gone outside the permissible range, and therefore no longer makes numeric sense.
	//
	//Note the implementation of ADD is the same in binary, this is just about how the numbers
	//are represented, so the word 10000100 can be both -124 and 132 depending upon the 
	//context the programming is using it in.
	//
	//10000100 =  132  or  -124
	//+00010001 = + 17      + 17
	//========    ===       ===     See, both are valid additions, but our interpretation of
	//10010101 =  149  or  -107     the context changes the value.
	//
	//In principle under the -128 to 127 range:
	//10000000 = -128, 11111111 = -1, 00000000 = 0, 00000000 = +1, 01111111 = +127
	//therefore negative numbers have the most significant set, positive numbers do not.
	//
	//To assist us, the 6502 can set the overflow flag, if the result of the addition has
	//wrapped around. V <- ~(A^M) & A^(A+M+C)
	//
	//Let's suppose we have A = 30, M = 10 and C = 0
	//A = 30 = 00011110
	//M = 10 = 00001010+
	//RESULT = 40 = 00101000
	//
	//Here we have not gone out of range. The resulting significant bit has not changed.
	//So let's make a truth table to understand when overflow has occurred. Here I take
	//the MSB of each component, where R is RESULT.
	//
	//A  M  R | V | A^R | A^M |~(A^M) | 
	//0  0  0 | 0 |  0  |  0  |   1   |
	//0  0  1 | 1 |  1  |  0  |   1   |
	//0  1  0 | 0 |  0  |  1  |   0   |
	//0  1  1 | 0 |  1  |  1  |   0   |  so V = ~(A^M) & (A^R)
	//1  0  0 | 0 |  1  |  1  |   0   |
	//1  0  1 | 0 |  0  |  1  |   0   |
	//1  1  0 | 1 |  1  |  0  |   1   |
	//1  1  1 | 0 |  0  |  0  |   1   |
	//
	//We can see how the above equation calculates V, based on A, M and R. V was chosen
	//based on the following hypothesis:
	//Positive Number + Positive Number = Negative Result -> Overflow
	//Negative Number + Negative Number = Positive Result -> Overflow
	//Positive Number + Negative Number = Either Result -> Cannot Overflow
	//Positive Number + Positive Number = Positive Result -> OK No Overflow
	//Negative Number + Negative Number = Negative Result -> OK NO Overflow
	public class ADC implements Operation 
	{
		public int Execute()
		{
			// Grab the data that we are adding to the accumulator.
			fetch();
			
			// Add is performed in 32-bit domain (int) for emulation to capture any
			// carry bit, which will exist in bit 8 of the 32-bit word.
			temp = a + fetched + GetFlag(FLAGS6502.C);
			
			// The carry flag out exists in the high byte bit 0.
			SetFlag(FLAGS6502.C, temp > 255);
			
			// The Zero flag is set if the result is 0.
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0);
			
			// The signed Overflow flag is set based on the description above.
			SetFlag(FLAGS6502.V, ((~(a ^ fetched) & (a ^ temp)) & 0x0080) != 0);
			
			// The negative flag is set to the most significant bit of the result.
			SetFlag(FLAGS6502.N, (temp & 0x80) != 0);
			
			// Load the result into the accumulator.
			a = temp & 0x00FF;
			
			// This instruction has the potential to require an additional clock cycle
			return 1;
		}
	};
	
	// Instruction: Bitwise Logic AND
	// Function:    A = A & M
	// Flags Out:   N, Z
	public class AND implements Operation 
	{
		public int Execute()
		{
			fetch();
			a = a & fetched;
			// Set the zero flag if the result is zero.
			SetFlag(FLAGS6502.Z, a == 0x00);
			// Set the negative flag if the result is negative, indicated by the 7th bit being set to 1.
			SetFlag(FLAGS6502.N, (a & 0x80) != 0);
			// Candidate for requiring additional clock cycles.
			return 1;
		}
	};
	
	// Instruction: Arithmetic Shift Left
	// Function:    A = C <- (A << 1) <- 0
	// Flags Out:   N, Z, C
	public class ASL implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = fetched << 1;
			SetFlag(FLAGS6502.C, (temp & 0xFF00) > 0);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
			SetFlag(FLAGS6502.N, (temp & 0x80) != 0);
			if (instruction.isImpliedAddressingMode)
				a = temp & 0x00FF;
			else
				write(addr_abs, temp & 0x00FF);
			return 0;
		}
	};
	
	// Instruction: Branch if Carry Clear
	// Function:    if(C == 0) pc = address 
	public class BCC implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.C) == 0)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	// Instruction: Branch if Carry Set
	// Function:    if(C == 1) pc = address
	public class BCS implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.C) == 1)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	// Instruction: Branch if Equal
	// Function:    if(Z == 1) pc = address
	public class BEQ implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.Z) == 1)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	// Tests bits in memory with accumulator.
	public class BIT implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = a & fetched;
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
			SetFlag(FLAGS6502.N, (fetched & (1 << 7)) != 0);
			SetFlag(FLAGS6502.V, (fetched & (1 << 6)) != 0);
			return 0;
		}
	};
	
	// Instruction: Branch if Negative
	// Function:    if(N == 1) pc = address
	public class BMI implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.N) == 1)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	public class BNE implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.Z) == 0)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	// Instruction: Branch if Positive
	// Function:    if(N == 0) pc = address
	public class BPL implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.N) == 0)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	// Instruction: Break
	// Function:    Program Sourced Interrupt
	public class BRK implements Operation 
	{
		public int Execute()
		{
			pc++;

			SetFlagON(FLAGS6502.I);
			
			// Write the program counter to the stack
			// High byte
			int write_location = (0x0100 + stkp) & 0xFFFF;
			write(write_location, pc >> 8);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			write_location--;
			//Low byte
			write(write_location, pc & 0x00FF);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			write_location--;
			// Write the status byte to the stack
			SetFlagON(FLAGS6502.B);
			write(write_location, status);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			SetFlagOFF(FLAGS6502.B);
	
			// Read the program counter from the last 2 bytes in the memory area
			pc = read(0xFFFE) | (read(0xFFFF) << 8);
			return 0;
		}
	};
	
	// Instruction: Branch if Overflow Clear
	// Function:    if(V == 0) pc = address
	public class BVC implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.V) == 0)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	// Instruction: Branch if Overflow Set
	// Function:    if(V == 1) pc = address
	public class BVS implements Operation 
	{
		public int Execute()
		{
			if (GetFlag(FLAGS6502.V) == 1)
			{
				// The branch instructions are special, because they require an additional cycle always.
				cycles++;
				// Note: The variable addr_rel can be positive or negative i.e. it is a signed byte.
				addr_abs = pc + addr_rel;
				// Since a 32bit int is used to store addr_abs, clear the 2 most significant bytes because we only want an unsigned
				// 16bit value.
				addr_abs &= 0xFFFF;
	
				// If the new address after adding the relative address crosses a page boundary, then another cycle needs to be
				// added. The addr_abs and pc pages are compared below for equality by only looking at the high byte which
				// represents the page number.
				if ((addr_abs & 0xFF00) != (pc & 0xFF00))
					cycles++;
	
				pc = addr_abs;
			}
			return 0;
		}
	};
	
	// Instruction: Clear Carry Flag
	// Function:    C = 0
	public class CLC implements Operation 
	{
		public int Execute()
		{
			SetFlagOFF(FLAGS6502.C);
			return 0;
		}
	};
	
	// Instruction: Clear Decimal Flag
	// Function:    D = 0
	public class CLD implements Operation 
	{
		public int Execute()
		{
			SetFlagOFF(FLAGS6502.D);
			return 0;
		}
	};
	
	// Instruction: Disable Interrupts / Clear Interrupt Flag
	// Function:    I = 0
	public class CLI implements Operation 
	{
		public int Execute()
		{
			SetFlagOFF(FLAGS6502.I);
			return 0;
		}
	};
	
	// Instruction: Clear Overflow Flag
	// Function:    V = 0
	public class CLV implements Operation 
	{
		public int Execute()
		{
			SetFlagOFF(FLAGS6502.V);
			return 0;
		}
	};
	
	// Instruction: Compare Accumulator
	// Function:    C <- A >= M      Z <- (A - M) == 0
	// Flags Out:   N, C, Z
	public class CMP implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = a - fetched;
			SetFlag(FLAGS6502.C, a >= fetched);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			return 1;
		}
	};
	
	// Instruction: Compare X Register
	// Function:    C <- X >= M      Z <- (X - M) == 0
	// Flags Out:   N, C, Z
	public class CPX implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = x - fetched;
			SetFlag(FLAGS6502.C, x >= fetched);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			return 0;
		}
	};
	
	// Instruction: Compare Y Register
	// Function:    C <- Y >= M      Z <- (Y - M) == 0
	// Flags Out:   N, C, Z
	public class CPY implements Operation
	{
		public int Execute()
		{
			fetch();
			temp = y - fetched;
			SetFlag(FLAGS6502.C, y >= fetched);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			return 0;
		}
	};
	
	// Instruction: Decrement Value at Memory Location
	// Function:    M = M - 1
	// Flags Out:   N, Z
	public class DEC implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = fetched - 1;
			write(addr_abs, temp & 0x00FF);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			return 0;
		}
	};
	
	// Instruction: Decrement X Register
	// Function:    X = X - 1
	// Flags Out:   N, Z
	public class DEX implements Operation 
	{
		public int Execute()
		{
			x--;
			// Since the X register is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store it, we need to reflect that it can underflow by only
			// using the last byte of the int.
			x &= 0xFF;
			SetFlag(FLAGS6502.Z, x == 0x00);
			SetFlag(FLAGS6502.N, (x & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Decrement Y Register
	// Function:    Y = Y - 1
	// Flags Out:   N, Z
	public class DEY implements Operation 
	{
		public int Execute()
		{
			y--;
			// Since the Y register is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store it, we need to reflect that it can underflow by only
			// using the last byte of the int.
			y &= 0xFF;
			SetFlag(FLAGS6502.Z, y == 0x00);
			SetFlag(FLAGS6502.N, (y & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Bitwise Logic XOR
	// Function:    A = A xor M
	// Flags Out:   N, Z
	public class EOR implements Operation 
	{
		public int Execute()
		{
			fetch();
			a = a ^ fetched;	
			SetFlag(FLAGS6502.Z, a == 0x00);
			SetFlag(FLAGS6502.N, (a & 0x80) != 0);
			return 1;
		}
	};
	
	// Instruction: Increment Value at Memory Location
	// Function:    M = M + 1
	// Flags Out:   N, Z
	public class INC implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = fetched + 1;
			write(addr_abs, temp & 0x00FF);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			return 0;
		}
	};
	
	// Instruction: Increment X Register
	// Function:    X = X + 1
	// Flags Out:   N, Z
	public class INX implements Operation 
	{
		public int Execute()
		{
			x++;
			// Since the X register is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store it, we need to reflect that it can overflow by only
			// using the last byte of the int.
			x &= 0xFF;
			SetFlag(FLAGS6502.Z, x == 0x00);
			SetFlag(FLAGS6502.N, (x & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Increment Y Register
	// Function:    Y = Y + 1
	// Flags Out:   N, Z
	public class INY implements Operation 
	{
		public int Execute()
		{
			y++;
			// Since the Y register is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store it, we need to reflect that it can overflow by only
			// using the last byte of the int.
			y &= 0xFF;
			SetFlag(FLAGS6502.Z, y == 0x00);
			SetFlag(FLAGS6502.N, (y & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Jump To Location
	// Function:    pc = address
	public class JMP implements Operation
	{
		public int Execute()
		{
			pc = addr_abs;
			return 0;
		}
	};
	
	// Instruction: Jump To Sub-Routine
	// Function:    Push current pc to stack, pc = address
	public class JSR implements Operation 
	{
		public int Execute()
		{
			pc--;
			
			int write_location = (0x0100 + stkp) & 0xFFFF;
			write(write_location, pc >> 8);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			write_location--;
			write(write_location, pc & 0x00FF);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
	
			pc = addr_abs;
			return 0;
		}
	};
	
	// Instruction: Load The Accumulator
	// Function:    A = M
	// Flags Out:   N, Z
	public class LDA implements Operation 
	{
		public int Execute()
		{
			fetch();
			a = fetched;
			SetFlag(FLAGS6502.Z, a == 0x00);
			SetFlag(FLAGS6502.N, (a & 0x80) != 0);
			return 1;
		}
	};
	
	// Instruction: Load The X Register
	// Function:    X = M
	// Flags Out:   N, Z
	public class LDX implements Operation 
	{
		public int Execute()
		{
			fetch();
			x = fetched;
			SetFlag(FLAGS6502.Z, x == 0x00);
			SetFlag(FLAGS6502.N, (x & 0x80) != 0);
			return 1;
		}
	};
	
	// Instruction: Load The Y Register
	// Function:    Y = M
	// Flags Out:   N, Z
	public class LDY implements Operation 
	{
		public int Execute()
		{
			fetch();
			y = fetched;
			SetFlag(FLAGS6502.Z, y == 0x00);
			SetFlag(FLAGS6502.N, (y & 0x80) != 0);
			return 1;
		}
	};
	
	// Shift one bit right (memory or accumulator)
	public class LSR implements Operation 
	{
		public int Execute()
		{
			fetch();
			SetFlag(FLAGS6502.C, (fetched & 0x0001) != 0);
			temp = fetched >> 1;	
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			if (instruction.isImpliedAddressingMode)
				a = temp & 0x00FF;
			else
				write(addr_abs, temp & 0x00FF);
			return 0;
		}
	};
	
	public class NOP implements Operation 
	{
		public int Execute()
		{
			// TODO: Look at adding more NOPs
			// I've only added a few NOPs here
			// based on https://wiki.nesdev.com/w/index.php/CPU_unofficial_opcodes
			// and will add more based on game compatibility, and ultimately
			// I'd like to cover all illegal opcodes too.
			switch (opcode) {
			case 0x04:
			case 0x14:
			case 0x34:
			case 0x44:
			case 0x54:
			case 0x64:
			case 0x74:
			case 0xD4:
			case 0xF4:
			case 0x80:
				pc++;				
				return 0;
			case 0x0C:
			case 0x1C:
			case 0x3C:
			case 0x5C:
			case 0x7C:
			case 0xDC:
			case 0xFC:
				pc++;
				pc++;
				return 0;
			}
			return 0;
		}
	};
	
	// Instruction: Bitwise Logic OR
	// Function:    A = A | M
	// Flags Out:   N, Z
	public class ORA implements Operation 
	{
		public int Execute()
		{
			fetch();
			a = a | fetched;
			SetFlag(FLAGS6502.Z, a == 0x00);
			SetFlag(FLAGS6502.N, (a & 0x80) != 0);
			return 1;
		}
	};
	
	// Instruction: Push Accumulator to Stack
	// Function:    A -> stack
	public class PHA implements Operation 
	{
		public int Execute()
		{
			write(offsetStackPointer(0x0100), a);
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			return 0;
		}
	};
	
	// Instruction: Push Status Register to Stack
	// Function:    status -> stack
	// Note:        Break flag is set to 1 before push
	public class PHP implements Operation 
	{
		public int Execute()
		{
			write(offsetStackPointer(0x0100), status | FLAGS6502.B | FLAGS6502.U);
			SetFlagOFF(FLAGS6502.B);
			SetFlagOFF(FLAGS6502.U);
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			return 0;
		}
	};
	
	// Instruction: Pop Accumulator off Stack
	// Function:    A <- stack
	// Flags Out:   N, Z
	public class PLA implements Operation 
	{
		public int Execute()
		{
			stkp++;
			// Since the stack pointer is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store the stack pointer, we need to reflect that it can overflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			a = read(offsetStackPointer(0x0100));
			SetFlag(FLAGS6502.Z, a == 0x00);
			SetFlag(FLAGS6502.N, (a & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Pop Status Register off Stack
	// Function:    Status <- stack
	public class PLP implements Operation 
	{
		public int Execute()
		{
			stkp++;
			// Since the stack pointer is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store the stack pointer, we need to reflect that it can overflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			status = read(offsetStackPointer(0x0100));
			SetFlagON(FLAGS6502.U);
			return 0;
		}
	};
	
	// Rotate one bit left
	public class ROL implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = (fetched << 1) | GetFlag(FLAGS6502.C);
			SetFlag(FLAGS6502.C, (temp & 0xFF00) != 0);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			if (instruction.isImpliedAddressingMode)
				a = temp & 0x00FF;
			else
				write(addr_abs, temp & 0x00FF);
			return 0;
		}
	};
	
	// Rotate one bit right
	public class ROR implements Operation 
	{
		public int Execute()
		{
			fetch();
			temp = (fetched >> 1) | GetFlag(FLAGS6502.C) << 7;
			SetFlag(FLAGS6502.C, (fetched & 0x01) != 0);
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			if (instruction.isImpliedAddressingMode)
				a = temp & 0x00FF;
			else
				write(addr_abs, temp & 0x00FF);
			return 0;
		}
	};
	
	// Return from interrupt
	public class RTI implements Operation 
	{
		public int Execute()
		{
			stkp++;
			// Since the stack pointer is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store the stack pointer, we need to reflect that it can overflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			int read_location = (0x0100 + stkp) & 0xFFFF;
			status = read(read_location);
			// Reset some of the status flags
			status &= ~FLAGS6502.B;
			status &= ~FLAGS6502.U;
	
			// Read the program counter off the stack
			
			// First the low byte
			stkp++;
			// Since the stack pointer is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store the stack pointer, we need to reflect that it can overflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			read_location++;
			pc = read(read_location);
			
			// Then the high byte
			stkp++;
			// Since the stack pointer is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store the stack pointer, we need to reflect that it can overflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			read_location++;
			pc |= read(read_location) << 8;
			
			return 0;
		}
	};
	
	// Return from subroutine
	public class RTS implements Operation 
	{
		public int Execute()
		{
			stkp++;
			// Since the stack pointer is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store the stack pointer, we need to reflect that it can overflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			int read_location = (0x0100 + stkp) & 0xFFFF;
			pc = read(read_location);
			stkp++;
			read_location++;
			// Since the stack pointer is incremented here, and it's only a 8bit value, it's possible that it can
			// overflow. Since I'm using an int to store the stack pointer, we need to reflect that it can overflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			pc |= read(read_location) << 8;
			pc++;

			return 0;
		}
	};
	
	// Instruction: Subtraction with Borrow In
	// Function:    A = A - M - (1 - C)
	// Flags Out:   C, V, N, Z
	//
	// Explanation:
	// Given the explanation for ADC above, we can reorganize our data
	// to use the same computation for addition, for subtraction by multiplying
	// the data by -1, i.e. make it negative.
	//
    //			A = A - M - (1 - C) 
    //		->  A = A - M - 1 + C
    //		->  A = A + -1 * (M + 1 - C)
    //		->  A = A + -M + -1 + C
    //		->  A = A + (~M + 1) + -1 + C
    //		->  A = A + ~M + (1 + -1) + C
    //		->  A = A + ~M + C 
	//
	// To make a signed positive number negative, we can invert the bits and add 1.
	//
	//  5 = 00000101
	// -5 = 11111010 + 00000001 = 11111011 (or 251 in our 0 to 255 range)
	//
	// The range is actually unimportant, because if I take the value 15, and add 251
	// to it, given we wrap around at 256, the result is 10, so it has effectively 
	// subtracted 5, which was the original intention. (15 + 251) % 256 = 10
	//
	// Note that the equation above used (1-C), but this got converted to + 1 + C.
	// This means we already have the +1, so all we need to do is invert the bits
	// of M, the data(!) therefore we can simply add, exactly the same way we did 
	// before.
	public class SBC implements Operation 
	{
		public int Execute()
		{
			// Grab the data that we are adding to the accumulator
			fetch();
			
			// We can invert the bottom 8 bits with bitwise xor.
			int value = fetched ^ 0x00FF;
			
			// Notice this is exactly the same as addition from here.
			
			// Add is performed in 32-bit domain (int) for emulation to capture any
			// carry bit, which will exist in bit 8 of the 32-bit word
			temp = a + value + GetFlag(FLAGS6502.C);
			
			// The carry flag out exists in the high byte bit 0.
			SetFlag(FLAGS6502.C, (temp & 0xFF00) != 0);
			
			// The Zero flag is set if the result is 0.
			SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0);
			
			// The signed Overflow flag is set based on the description above.
			SetFlag(FLAGS6502.V, ((temp ^ a) & (temp ^ value) & 0x0080) != 0);
			
			// The negative flag is set to the most significant bit of the result.
			SetFlag(FLAGS6502.N, (temp & 0x0080) != 0);
			
			// Load the result into the accumulator.
			a = temp & 0x00FF;
			
			// This instruction has the potential to require an additional clock cycle.
			return 1;
		}
	};
	
	// Instruction: Set Carry Flag
	// Function:    C = 1
	public class SEC implements Operation 
	{
		public int Execute()
		{
			SetFlagON(FLAGS6502.C);
			return 0;
		}
	};
	
	// Instruction: Set Decimal Flag
	// Function:    D = 1
	public class SED implements Operation 
	{
		public int Execute()
		{
			SetFlagON(FLAGS6502.D);
			return 0;
		}
	};
	
	// Instruction: Set Interrupt Flag / Enable Interrupts
	// Function:    I = 1
	public class SEI implements Operation 
	{
		public int Execute()
		{
			SetFlagON(FLAGS6502.I);
			return 0;
		}
	};
	
	// Instruction: Store Accumulator at Address
	// Function:    M = A
	public class STA implements Operation 
	{
		public int Execute()
		{
			write(addr_abs, a);
			return 0;
		}
	};
	
	// Instruction: Store X Register at Address
	// Function:    M = X
	public class STX implements Operation 
	{
		public int Execute()
		{
			write(addr_abs, x);
			return 0;
		}
	};
	
	// Instruction: Store Y Register at Address
	// Function:    M = Y
	public class STY implements Operation 
	{
		public int Execute()
		{
			write(addr_abs, y);
			return 0;
		}
	};
	
	// Instruction: Transfer Accumulator to X Register
	// Function:    X = A
	// Flags Out:   N, Z
	public class TAX implements Operation 
	{
		public int Execute()
		{
			x = a;
			SetFlag(FLAGS6502.Z, x == 0x00);
			SetFlag(FLAGS6502.N, (x & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Transfer Accumulator to Y Register
	// Function:    Y = A
	// Flags Out:   N, Z
	public class TAY implements Operation 
	{
		public int Execute()
		{
			y = a;
			SetFlag(FLAGS6502.Z, y == 0x00);
			SetFlag(FLAGS6502.N, (y & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Transfer Stack Pointer to X Register
	// Function:    X = stack pointer
	// Flags Out:   N, Z
	public class TSX implements Operation 
	{
		public int Execute()
		{
			x = stkp;
			SetFlag(FLAGS6502.Z, x == 0x00);
			SetFlag(FLAGS6502.N, (x & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Transfer X Register to Accumulator
	// Function:    A = X
	// Flags Out:   N, Z
	public class TXA implements Operation 
	{
		public int Execute()
		{
			a = x;
			SetFlag(FLAGS6502.Z, a == 0x00);
			SetFlag(FLAGS6502.N, (a & 0x80) != 0);
			return 0;
		}
	};
	
	// Instruction: Transfer X Register to Stack Pointer
	// Function:    stack pointer = X
	public class TXS implements Operation 
	{
		public int Execute()
		{
			stkp = x;
			return 0;
		}
	};
	
	// Instruction: Transfer Y Register to Accumulator
	// Function:    A = Y
	// Flags Out:   N, Z
	public class TYA implements Operation 
	{
		public int Execute()
		{
			a = y;
			SetFlag(FLAGS6502.Z, a == 0x00);
			SetFlag(FLAGS6502.N, (a & 0x80) != 0);
			return 0;
		}
	};
	
	public class XXX implements Operation 
	{
		public int Execute()
		{
			// "Invalid" opcodes. I have not taken the time yet to implement these, but these are the ones
			// known at least from the nestest.nes test ROM.
			switch (opcode) {
			case 0xA3: //LAX
			case 0x83: //SAX
			case 0xEB: //SBC
			case 0xC3: //DCP
			case 0xE3: //ISB
			case 0x03: //SLO
			case 0x23: //RLA
			case 0x43: //SRE
			case 0x63: //RRA
			}
			return 0;
		}
	};
	
	// Initialize the lookup table for opcode and address mode.
	private void initializeLookupTable()
	{
		lookup[0] = new Instruction( "BRK", OPCODE_BRK, ADDRESS_MODE_IMM,  7);
		lookup[1] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_IZX,  6);
		lookup[2] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[3] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[4] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  3);
		lookup[5] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_ZP0,  3);
		lookup[6] = new Instruction( "ASL", OPCODE_ASL, ADDRESS_MODE_ZP0,  5);
		lookup[7] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[8] = new Instruction( "PHP", OPCODE_PHP, ADDRESS_MODE_IMP,  3);
		lookup[9] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_IMM,  2);
		lookup[10] = new Instruction( "ASL", OPCODE_ASL, ADDRESS_MODE_IMP,  2);
		lookup[11] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[12] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[13] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_ABS,  4);
		lookup[14] = new Instruction( "ASL", OPCODE_ASL, ADDRESS_MODE_ABS,  6);
		lookup[15] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[16] = new Instruction( "BPL", OPCODE_BPL, ADDRESS_MODE_REL,  2);
		lookup[17] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_IZY,  5);
		lookup[18] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[19] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[20] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[21] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_ZPX,  4);
		lookup[22] = new Instruction( "ASL", OPCODE_ASL, ADDRESS_MODE_ZPX,  6);
		lookup[23] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[24] = new Instruction( "CLC", OPCODE_CLC, ADDRESS_MODE_IMP,  2);
		lookup[25] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_ABY,  4);
		lookup[26] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[27] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[28] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[29] = new Instruction( "ORA", OPCODE_ORA, ADDRESS_MODE_ABX,  4);
		lookup[30] = new Instruction( "ASL", OPCODE_ASL, ADDRESS_MODE_ABX,  7);
		lookup[31] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[32] = new Instruction( "JSR", OPCODE_JSR, ADDRESS_MODE_ABS,  6);
		lookup[33] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_IZX,  6);
		lookup[34] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[35] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[36] = new Instruction( "BIT", OPCODE_BIT, ADDRESS_MODE_ZP0,  3);
		lookup[37] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_ZP0,  3);
		lookup[38] = new Instruction( "ROL", OPCODE_ROL, ADDRESS_MODE_ZP0,  5);
		lookup[39] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[40] = new Instruction( "PLP", OPCODE_PLP, ADDRESS_MODE_IMP,  4);
		lookup[41] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_IMM,  2);
		lookup[42] = new Instruction( "ROL", OPCODE_ROL, ADDRESS_MODE_IMP,  2);
		lookup[43] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[44] = new Instruction( "BIT", OPCODE_BIT, ADDRESS_MODE_ABS,  4);
		lookup[45] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_ABS,  4);
		lookup[46] = new Instruction( "ROL", OPCODE_ROL, ADDRESS_MODE_ABS,  6);
		lookup[47] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[48] = new Instruction( "BMI", OPCODE_BMI, ADDRESS_MODE_REL,  2);
		lookup[49] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_IZY,  5);
		lookup[50] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[51] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[52] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[53] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_ZPX,  4);
		lookup[54] = new Instruction( "ROL", OPCODE_ROL, ADDRESS_MODE_ZPX,  6);
		lookup[55] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[56] = new Instruction( "SEC", OPCODE_SEC, ADDRESS_MODE_IMP,  2);
		lookup[57] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_ABY,  4);
		lookup[58] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[59] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[60] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[61] = new Instruction( "AND", OPCODE_AND, ADDRESS_MODE_ABX,  4);
		lookup[62] = new Instruction( "ROL", OPCODE_ROL, ADDRESS_MODE_ABX,  7);
		lookup[63] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[64] = new Instruction( "RTI", OPCODE_RTI, ADDRESS_MODE_IMP,  6);
		lookup[65] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_IZX,  6);
		lookup[66] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[67] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[68] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  3);
		lookup[69] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_ZP0,  3);
		lookup[70] = new Instruction( "LSR", OPCODE_LSR, ADDRESS_MODE_ZP0,  5);
		lookup[71] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[72] = new Instruction( "PHA", OPCODE_PHA, ADDRESS_MODE_IMP,  3);
		lookup[73] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_IMM,  2);
		lookup[74] = new Instruction( "LSR", OPCODE_LSR, ADDRESS_MODE_IMP,  2);
		lookup[75] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[76] = new Instruction( "JMP", OPCODE_JMP, ADDRESS_MODE_ABS,  3);
		lookup[77] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_ABS,  4);
		lookup[78] = new Instruction( "LSR", OPCODE_LSR, ADDRESS_MODE_ABS,  6);
		lookup[79] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[80] = new Instruction( "BVC", OPCODE_BVC, ADDRESS_MODE_REL,  2);
		lookup[81] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_IZY,  5);
		lookup[82] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[83] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[84] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[85] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_ZPX,  4);
		lookup[86] = new Instruction( "LSR", OPCODE_LSR, ADDRESS_MODE_ZPX,  6);
		lookup[87] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[88] = new Instruction( "CLI", OPCODE_CLI, ADDRESS_MODE_IMP,  2);
		lookup[89] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_ABY,  4);
		lookup[90] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[91] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[92] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[93] = new Instruction( "EOR", OPCODE_EOR, ADDRESS_MODE_ABX,  4);
		lookup[94] = new Instruction( "LSR", OPCODE_LSR, ADDRESS_MODE_ABX,  7);
		lookup[95] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[96] = new Instruction( "RTS", OPCODE_RTS, ADDRESS_MODE_IMP,  6);
		lookup[97] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_IZX,  6);
		lookup[98] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[99] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[100] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  3);
		lookup[101] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_ZP0,  3);
		lookup[102] = new Instruction( "ROR", OPCODE_ROR, ADDRESS_MODE_ZP0,  5);
		lookup[103] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[104] = new Instruction( "PLA", OPCODE_PLA, ADDRESS_MODE_IMP,  4);
		lookup[105] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_IMM,  2);
		lookup[106] = new Instruction( "ROR", OPCODE_ROR, ADDRESS_MODE_IMP,  2);
		lookup[107] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[108] = new Instruction( "JMP", OPCODE_JMP, ADDRESS_MODE_IND,  5);
		lookup[109] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_ABS,  4);
		lookup[110] = new Instruction( "ROR", OPCODE_ROR, ADDRESS_MODE_ABS,  6);
		lookup[111] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[112] = new Instruction( "BVS", OPCODE_BVS, ADDRESS_MODE_REL,  2);
		lookup[113] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_IZY,  5);
		lookup[114] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[115] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[116] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[117] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_ZPX,  4);
		lookup[118] = new Instruction( "ROR", OPCODE_ROR, ADDRESS_MODE_ZPX,  6);
		lookup[119] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[120] = new Instruction( "SEI", OPCODE_SEI, ADDRESS_MODE_IMP,  2);
		lookup[121] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_ABY,  4);
		lookup[122] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[123] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[124] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[125] = new Instruction( "ADC", OPCODE_ADC, ADDRESS_MODE_ABX,  4);
		lookup[126] = new Instruction( "ROR", OPCODE_ROR, ADDRESS_MODE_ABX,  7);
		lookup[127] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[128] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[129] = new Instruction( "STA", OPCODE_STA, ADDRESS_MODE_IZX,  6);
		lookup[130] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[131] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[132] = new Instruction( "STY", OPCODE_STY, ADDRESS_MODE_ZP0,  3);
		lookup[133] = new Instruction( "STA", OPCODE_STA, ADDRESS_MODE_ZP0,  3);
		lookup[134] = new Instruction( "STX", OPCODE_STX, ADDRESS_MODE_ZP0,  3);
		lookup[135] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  3);
		lookup[136] = new Instruction( "DEY", OPCODE_DEY, ADDRESS_MODE_IMP,  2);
		lookup[137] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[138] = new Instruction( "TXA", OPCODE_TXA, ADDRESS_MODE_IMP,  2);
		lookup[139] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[140] = new Instruction( "STY", OPCODE_STY, ADDRESS_MODE_ABS,  4);
		lookup[141] = new Instruction( "STA", OPCODE_STA, ADDRESS_MODE_ABS,  4);
		lookup[142] = new Instruction( "STX", OPCODE_STX, ADDRESS_MODE_ABS,  4);
		lookup[143] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  4);
		lookup[144] = new Instruction( "BCC", OPCODE_BCC, ADDRESS_MODE_REL,  2);
		lookup[145] = new Instruction( "STA", OPCODE_STA, ADDRESS_MODE_IZY,  6);
		lookup[146] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[147] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[148] = new Instruction( "STY", OPCODE_STY, ADDRESS_MODE_ZPX,  4);
		lookup[149] = new Instruction( "STA", OPCODE_STA, ADDRESS_MODE_ZPX,  4);
		lookup[150] = new Instruction( "STX", OPCODE_STX, ADDRESS_MODE_ZPY,  4);
		lookup[151] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  4);
		lookup[152] = new Instruction( "TYA", OPCODE_TYA, ADDRESS_MODE_IMP,  2);
		lookup[153] = new Instruction( "STA", OPCODE_STA, ADDRESS_MODE_ABY,  5);
		lookup[154] = new Instruction( "TXS", OPCODE_TXS, ADDRESS_MODE_IMP,  2);
		lookup[155] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[156] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  5);
		lookup[157] = new Instruction( "STA", OPCODE_STA, ADDRESS_MODE_ABX,  5);
		lookup[158] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[159] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[160] = new Instruction( "LDY", OPCODE_LDY, ADDRESS_MODE_IMM,  2);
		lookup[161] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_IZX,  6);
		lookup[162] = new Instruction( "LDX", OPCODE_LDX, ADDRESS_MODE_IMM,  2);
		lookup[163] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[164] = new Instruction( "LDY", OPCODE_LDY, ADDRESS_MODE_ZP0,  3);
		lookup[165] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_ZP0,  3);
		lookup[166] = new Instruction( "LDX", OPCODE_LDX, ADDRESS_MODE_ZP0,  3);
		lookup[167] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  3);
		lookup[168] = new Instruction( "TAY", OPCODE_TAY, ADDRESS_MODE_IMP,  2);
		lookup[169] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_IMM,  2);
		lookup[170] = new Instruction( "TAX", OPCODE_TAX, ADDRESS_MODE_IMP,  2);
		lookup[171] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[172] = new Instruction( "LDY", OPCODE_LDY, ADDRESS_MODE_ABS,  4);
		lookup[173] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_ABS,  4);
		lookup[174] = new Instruction( "LDX", OPCODE_LDX, ADDRESS_MODE_ABS,  4);
		lookup[175] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  4);
		lookup[176] = new Instruction( "BCS", OPCODE_BCS, ADDRESS_MODE_REL,  2);
		lookup[177] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_IZY,  5);
		lookup[178] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[179] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[180] = new Instruction( "LDY", OPCODE_LDY, ADDRESS_MODE_ZPX,  4);
		lookup[181] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_ZPX,  4);
		lookup[182] = new Instruction( "LDX", OPCODE_LDX, ADDRESS_MODE_ZPY,  4);
		lookup[183] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  4);
		lookup[184] = new Instruction( "CLV", OPCODE_CLV, ADDRESS_MODE_IMP,  2);
		lookup[185] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_ABY,  4);
		lookup[186] = new Instruction( "TSX", OPCODE_TSX, ADDRESS_MODE_IMP,  2);
		lookup[187] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  4);
		lookup[188] = new Instruction( "LDY", OPCODE_LDY, ADDRESS_MODE_ABX,  4);
		lookup[189] = new Instruction( "LDA", OPCODE_LDA, ADDRESS_MODE_ABX,  4);
		lookup[190] = new Instruction( "LDX", OPCODE_LDX, ADDRESS_MODE_ABY,  4);
		lookup[191] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  4);
		lookup[192] = new Instruction( "CPY", OPCODE_CPY, ADDRESS_MODE_IMM,  2);
		lookup[193] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_IZX,  6);
		lookup[194] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[195] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[196] = new Instruction( "CPY", OPCODE_CPY, ADDRESS_MODE_ZP0,  3);
		lookup[197] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_ZP0,  3);
		lookup[198] = new Instruction( "DEC", OPCODE_DEC, ADDRESS_MODE_ZP0,  5);
		lookup[199] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[200] = new Instruction( "INY", OPCODE_INY, ADDRESS_MODE_IMP,  2);
		lookup[201] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_IMM,  2);
		lookup[202] = new Instruction( "DEX", OPCODE_DEX, ADDRESS_MODE_IMP,  2);
		lookup[203] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[204] = new Instruction( "CPY", OPCODE_CPY, ADDRESS_MODE_ABS,  4);
		lookup[205] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_ABS,  4);
		lookup[206] = new Instruction( "DEC", OPCODE_DEC, ADDRESS_MODE_ABS,  6);
		lookup[207] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[208] = new Instruction( "BNE", OPCODE_BNE, ADDRESS_MODE_REL,  2);
		lookup[209] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_IZY,  5);
		lookup[210] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[211] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[212] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[213] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_ZPX,  4);
		lookup[214] = new Instruction( "DEC", OPCODE_DEC, ADDRESS_MODE_ZPX,  6);
		lookup[215] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[216] = new Instruction( "CLD", OPCODE_CLD, ADDRESS_MODE_IMP,  2);
		lookup[217] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_ABY,  4);
		lookup[218] = new Instruction( "NOP", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[219] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[220] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[221] = new Instruction( "CMP", OPCODE_CMP, ADDRESS_MODE_ABX,  4);
		lookup[222] = new Instruction( "DEC", OPCODE_DEC, ADDRESS_MODE_ABX,  7);
		lookup[223] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[224] = new Instruction( "CPX", OPCODE_CPX, ADDRESS_MODE_IMM,  2);
		lookup[225] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_IZX,  6);
		lookup[226] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[227] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[228] = new Instruction( "CPX", OPCODE_CPX, ADDRESS_MODE_ZP0,  3);
		lookup[229] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_ZP0,  3);
		lookup[230] = new Instruction( "INC", OPCODE_INC, ADDRESS_MODE_ZP0,  5);
		lookup[231] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  5);
		lookup[232] = new Instruction( "INX", OPCODE_INX, ADDRESS_MODE_IMP,  2);
		lookup[233] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_IMM,  2);
		lookup[234] = new Instruction( "NOP", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[235] = new Instruction( "???", OPCODE_SBC, ADDRESS_MODE_IMP,  2);
		lookup[236] = new Instruction( "CPX", OPCODE_CPX, ADDRESS_MODE_ABS,  4);
		lookup[237] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_ABS,  4);
		lookup[238] = new Instruction( "INC", OPCODE_INC, ADDRESS_MODE_ABS,  6);
		lookup[239] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[240] = new Instruction( "BEQ", OPCODE_BEQ, ADDRESS_MODE_REL,  2);
		lookup[241] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_IZY,  5);
		lookup[242] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  2);
		lookup[243] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  8);
		lookup[244] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[245] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_ZPX,  4);
		lookup[246] = new Instruction( "INC", OPCODE_INC, ADDRESS_MODE_ZPX,  6);
		lookup[247] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  6);
		lookup[248] = new Instruction( "SED", OPCODE_SED, ADDRESS_MODE_IMP,  2);
		lookup[249] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_ABY,  4);
		lookup[250] = new Instruction( "NOP", OPCODE_NOP, ADDRESS_MODE_IMP,  2);
		lookup[251] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
		lookup[252] = new Instruction( "???", OPCODE_NOP, ADDRESS_MODE_IMP,  4);
		lookup[253] = new Instruction( "SBC", OPCODE_SBC, ADDRESS_MODE_ABX,  4);
		lookup[254] = new Instruction( "INC", OPCODE_INC, ADDRESS_MODE_ABX,  7);
		lookup[255] = new Instruction( "???", OPCODE_XXX, ADDRESS_MODE_IMP,  7);
	}
	
	private int offsetStackPointer(int offset)
	{
		// Since an offset is added to the stack pointer this could overflow a 16bit boundary, so only the remainder
		// should be used as he does in his code.
		int calculated_stack_address = offset + stkp;
		calculated_stack_address &= 0xFFFF;
		return calculated_stack_address;
	}
	
	// Perform one clock cycles' worth of emulation.
	public void clock()
	{
		// Each instruction requires a variable number of clock cycles to execute.
		// In this emulation, I only care about the final result and so I perform
		// the entire computation in one hit. In hardware, each clock cycle would
		// perform "microcode" style transformations of the CPUs state.
		//
		// To remain compliant with connected devices, it's important that the 
		// emulation also takes "time" in order to execute instructions, so I
		// implement that delay by simply counting down the cycles required by 
		// the instruction. When it reaches 0, the instruction is complete, and
		// the next one is ready to be executed.
		if (cycles == 0)
		{
			// Read next instruction byte. This 8-bit value is used to index
			// the translation table to get the relevant information about
			// how to implement the instruction.
			opcode = read(pc);
			
			// Always set the unused status flag bit to 1.
			SetFlagON(FLAGS6502.U);
			
			// Increment program counter, we read the opcode byte.
			pc++;

			instruction = lookup[opcode];
			
			// Get starting number of cycles.
			cycles = instruction.cycles;
			
			// Perform fetch of intermediate data using the required addressing mode
			/*unsigned 8bit*/ int additional_cycle1 = instruction.addrmode.Execute();
			
			// Perform operation
			/*unsigned 8bit*/ int additional_cycle2 = instruction.operate.Execute();
			
			// The address mode and opcode may have altered the number
			// of cycles this instruction requires before its completed.
			cycles += (additional_cycle1 & additional_cycle2);
			
			// Always set the unused status flag bit to 1.
			SetFlagON(FLAGS6502.U);
		}
		
		cycles--;
	}
	
	///////////////////////////////////////////////////////////////////////////////
	//EXTERNAL INPUTS
	
	// Forces the 6502 into a known state. This is hard-wired inside the CPU. The
	// registers are set to 0x00, the status register is cleared except for the unused
	// bit which remains at 1. An absolute address is read from location 0xFFFC
	// which contains a second address that the program counter is set to. This 
	// allows the programmer to jump to a known and programmable location in the
	// memory to start executing from. Typically the programmer would set the value
	// at location 0xFFFC at compile time.
	public void reset()
	{
		opcode = 0x00;
		
		// Get address to set program counter to
		addr_abs = 0xFFFC;
		int lo = read(addr_abs + 0);
		int high_byte_address = addr_abs + 1;
		int hi = read(high_byte_address);

		// Set it
		pc = (hi << 8) | lo;

		// Reset internal registers.
		a = 0;
		x = 0;
		y = 0;
		stkp = 0xFD;
		status = 0x00 | FLAGS6502.U;

		// Clear internal helper variables.
		addr_rel = 0x0000;
		addr_abs = 0x0000;
		fetched = 0x00;

		// Reset takes time.
		cycles = 8;
	}
	
	// Interrupt requests are a complex operation and only happen if the
	// "disable interrupt" flag is 0. IRQs can happen at any time, but
	// you don't want them to be destructive to the operation of the running 
	// program. Therefore the current instruction is allowed to finish
	// (which I facilitate by doing the whole thing when cycles == 0) and 
	// then the current program counter is stored on the stack. Then the
	// current status register is stored on the stack. When the routine
	// that services the interrupt has finished, the status register
	// and program counter can be restored to how they where before it 
	// occurred. This is implemented by the "RTI" instruction. Once the IRQ
	// has happened, in a similar way to a reset, a programmable address
	// is read from hard coded location 0xFFFE, which is subsequently
	// set to the program counter.
	public void irq()
	{
		// If interrupts are allowed
		if (GetFlag(FLAGS6502.I) == 0)
		{
			// Push the program counter to the stack. It's 16-bits don't
			// forget so that takes two pushes.
			
			// Push high byte
			int write_location = (0x0100 + stkp) & 0xFFFF;
			write(write_location, pc >> 8);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			write_location--;
			// Push low byte
			write(write_location, pc & 0x00FF);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;
			write_location--;

			// Set the required status bits
			SetFlagOFF(FLAGS6502.B);
			SetFlagON(FLAGS6502.U);
			SetFlagON(FLAGS6502.I);
			
			// Then Push the status register to the stack.
			write(write_location, status);
			
			stkp--;
			// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
			// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
			// using the last byte of the int.
			stkp &= 0xFF;

			// Read new program counter location from fixed address
			int lo = read(0xFFFE);
			int hi = read(0xFFFF);
			pc = (hi << 8) | lo;

			// IRQs take time
			cycles = 7;
		}
	}
	
	// A Non-Maskable Interrupt cannot be ignored. It behaves in exactly the
	// same way as a regular IRQ, but reads the new program counter address
	// from location 0xFFFA.
	public void nmi()
	{		
		// Push the program counter to the stack. It's 16-bits don't
		// forget so that takes two pushes
		
		// Push high byte
		int write_location = (0x0100 + stkp) & 0xFFFF;
		write(write_location, pc >> 8);
		
		stkp--;
		// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
		// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
		// using the last byte of the int.
		stkp &= 0xFF;
		write_location--;
		// Push low byte
		write(write_location, pc & 0x00FF);
		
		stkp--;
		// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
		// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
		// using the last byte of the int.
		stkp &= 0xFF;
		write_location--;

		// Set the required status bits
		SetFlagOFF(FLAGS6502.B);
		SetFlagON(FLAGS6502.U);
		SetFlagON(FLAGS6502.I);
		
		// Then Push the status register to the stack
		write(write_location, status);
		
		stkp--;
		// Since the stack pointer is decremented here, and it's only a 8bit value, it's possible that it can
		// underflow. Since I'm using an int to store the stack pointer, we need to reflect that it can underflow by only
		// using the last byte of the int.
		stkp &= 0xFF;

		// Read new program counter location from fixed address
		int lo = read(0xFFFA);
		int hi = read(0xFFFB);
		pc = (hi << 8) | lo;

		// IRQs take time
		cycles = 8;
	}
	
	// This function sources the data used by the instruction into 
	// a convenient numeric variable. Some instructions don't have to 
	// fetch data as the source is implied by the instruction. For example
	// "INX" increments the X register. There is no additional data
	// required. For all other addressing modes, the data resides at 
	// the location held within addr_abs, so it is read from there. 
	// Immediate address mode exploits this slightly, as that has
	// set addr_abs = pc + 1, so it fetches the data from the
	// next byte for example "LDA $FF" just loads the accumulator with
	// 256, i.e. no far reaching memory fetch is required. "fetched"
	// is a variable global to the CPU, and is set by calling this 
	// function. It also returns it for convenience.
	private /*unsigned 8bit*/ int fetch()
	{
		if (!(instruction.isImpliedAddressingMode))
			fetched = read(addr_abs);
		return fetched;
	}
	
	///////////////////////////////////////////////////////////////////////////////
	//BUS CONNECTIVITY

	//Reads an 8-bit byte from the bus, located at the specified 16-bit address
	private /*unsigned 8bit*/ int read(/*unsigned 16bit*/ int addr)
	{
		// In normal operation "read only" is set to false. This may seem odd. Some
		// devices on the bus may change state when they are read from, and this 
		// is intentional under normal circumstances. However the disassembler will
		// want to read the data at an address without changing the state of the
		// devices on the bus.
		return bus.cpuRead(addr, false);
	}
	
	// Writes a byte to the bus at the specified address.
	private void write(/*unsigned 16bit*/ int addr, /*unsigned 8bit*/ int data)
	{
		bus.cpuWrite(addr, data);
	}
	
	///////////////////////////////////////////////////////////////////////////////
	//FLAG FUNCTIONS

	//Returns the value of a specific bit of the status register.
	public /*unsigned 8bit*/ int GetFlag(/*unsigned 8bit*/ int f)
	{
		return (status & f) > 0 ? 1 : 0;
	}
	
	// Sets or clears a specific bit of the status register.
	private void SetFlag(/*unsigned 8bit*/ int f, boolean v)
	{
		if (v)
			status |= f;
		else
			status &= ~f;
	}
	
	// Sets a specific bit of the status register.
	private void SetFlagON(/*unsigned 8bit*/ int f)
	{
		status |= f;
	}
	
	// Clears a specific bit of the status register.
	private void SetFlagOFF(/*unsigned 8bit*/ int f)
	{
		status &= ~f;
	}
	
	///////////////////////////////////////////////////////////////////////////////
	//HELPER FUNCTIONS
	
	public boolean complete()
	{
		return cycles == 0;
	}
	
	// The status register stores 8 flags. I've enumerated these here for ease
	// of access. You can access the status register directly since its public.
	// The bits have different interpretations depending upon the context and 
	// instruction being executed.
	public static class FLAGS6502
	{
		public static final /*unsigned 8bit*/ int C = (1 << 0); // Carry Bit
		public static final /*unsigned 8bit*/ int Z = (1 << 1); // Zero
		public static final /*unsigned 8bit*/ int I = (1 << 2); // Disable Interrupts
		public static final /*unsigned 8bit*/ int D = (1 << 3); // Decimal Mode (unused in this implementation)
		public static final /*unsigned 8bit*/ int B = (1 << 4); // Break
		public static final /*unsigned 8bit*/ int U = (1 << 5); // Unused
		public static final /*unsigned 8bit*/ int V = (1 << 6); // Overflow
		public static final /*unsigned 8bit*/ int N = (1 << 7); // Negative
	}
	
	public static class Instruction
	{
		public String name;
		public Operation operate;
		public Operation addrmode;
		public int cycles = 0;
		public boolean isImpliedAddressingMode = false;
		
		public Instruction(String name, Operation operate, Operation addrmode, int cycles)
		{
			this.name = name;
			this.operate = operate;
			this.addrmode = addrmode;
			this.cycles = cycles;
			isImpliedAddressingMode = (addrmode instanceof IMP);
		}
	}
}
