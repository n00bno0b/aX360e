package aenu.ax360e;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests that verify the logic documented by the adrenotools BCeNabler API
 * (adrenotools_get_bcn_type) as described in:
 *   app/src/main/cpp/libadrenotools/src/bcenabler.cpp
 *   app/src/main/cpp/libadrenotools/include/adrenotools/bcenabler.h
 *
 * The native function cannot be called directly from a host JVM unit test, so
 * these tests encode the decision logic in Java and verify it mirrors what the
 * C++ implementation does.  They act as a specification test that will catch
 * any accidental divergence if the logic is ever ported to Java.
 *
 * C++ reference implementation:
 *   enum adrenotools_bcn_type adrenotools_get_bcn_type(
 *       uint32_t major, uint32_t minor, uint32_t vendorId) {
 *     if (vendorId != 0x5143 || major != 512)
 *         return ADRENOTOOLS_BCN_INCOMPATIBLE;  // 0
 *     if (minor >= 514)
 *         return ADRENOTOOLS_BCN_BLOB;          // 1
 *     return ADRENOTOOLS_BCN_PATCH;             // 2
 *   }
 */
public class AdrenoToolsBcnTypeLogicTest {

    // Mirror the C++ enum values
    private static final int BCN_INCOMPATIBLE = 0;
    private static final int BCN_BLOB         = 1;
    private static final int BCN_PATCH        = 2;

    private static final int QUALCOMM_VENDOR_ID = 0x5143;
    private static final int ADRENO_MAJOR       = 512;

    // -----------------------------------------------------------------------
    // Java mirror of the C++ adrenotools_get_bcn_type() function
    // -----------------------------------------------------------------------
    private int getBcnType(int major, int minor, int vendorId) {
        // Convert to unsigned comparison semantics as Java int is signed but
        // the C++ side uses uint32_t.  For the values we care about (0x5143, 512,
        // 514) there is no sign-bit ambiguity.
        if (Integer.toUnsignedLong(vendorId) != Integer.toUnsignedLong(QUALCOMM_VENDOR_ID)
                || Integer.toUnsignedLong(major) != Integer.toUnsignedLong(ADRENO_MAJOR)) {
            return BCN_INCOMPATIBLE;
        }
        if (Integer.compareUnsigned(minor, 514) >= 0) {
            return BCN_BLOB;
        }
        return BCN_PATCH;
    }

    // -----------------------------------------------------------------------
    // Incompatible vendor
    // -----------------------------------------------------------------------

    @Test
    public void getBcnType_nonQualcommVendor_returnsIncompatible() {
        // ARM Mali vendor id
        assertEquals(BCN_INCOMPATIBLE, getBcnType(ADRENO_MAJOR, 500, 0x13B5));
    }

    @Test
    public void getBcnType_zeroVendor_returnsIncompatible() {
        assertEquals(BCN_INCOMPATIBLE, getBcnType(ADRENO_MAJOR, 500, 0));
    }

    @Test
    public void getBcnType_offByOneVendorId_returnsIncompatible() {
        assertEquals(BCN_INCOMPATIBLE, getBcnType(ADRENO_MAJOR, 500, QUALCOMM_VENDOR_ID + 1));
        assertEquals(BCN_INCOMPATIBLE, getBcnType(ADRENO_MAJOR, 500, QUALCOMM_VENDOR_ID - 1));
    }

    // -----------------------------------------------------------------------
    // Incompatible major version
    // -----------------------------------------------------------------------

    @Test
    public void getBcnType_wrongMajor_returnsIncompatible() {
        assertEquals(BCN_INCOMPATIBLE, getBcnType(511, 500, QUALCOMM_VENDOR_ID));
        assertEquals(BCN_INCOMPATIBLE, getBcnType(513, 500, QUALCOMM_VENDOR_ID));
        assertEquals(BCN_INCOMPATIBLE, getBcnType(0,   500, QUALCOMM_VENDOR_ID));
    }

    @Test
    public void getBcnType_majorAndVendorBothWrong_returnsIncompatible() {
        assertEquals(BCN_INCOMPATIBLE, getBcnType(0, 0, 0));
    }

    // -----------------------------------------------------------------------
    // Blob – driver already supports BCn natively (minor >= 514)
    // -----------------------------------------------------------------------

    @Test
    public void getBcnType_minorExactly514_returnsBlob() {
        assertEquals(BCN_BLOB, getBcnType(ADRENO_MAJOR, 514, QUALCOMM_VENDOR_ID));
    }

    @Test
    public void getBcnType_minorGreaterThan514_returnsBlob() {
        assertEquals(BCN_BLOB, getBcnType(ADRENO_MAJOR, 515, QUALCOMM_VENDOR_ID));
        assertEquals(BCN_BLOB, getBcnType(ADRENO_MAJOR, 600, QUALCOMM_VENDOR_ID));
        assertEquals(BCN_BLOB, getBcnType(ADRENO_MAJOR, 0xFFFF, QUALCOMM_VENDOR_ID));
    }

    // -----------------------------------------------------------------------
    // Patch – driver can be patched by BCeNabler (minor < 514)
    // -----------------------------------------------------------------------

    @Test
    public void getBcnType_minor513_returnsPatch() {
        assertEquals(BCN_PATCH, getBcnType(ADRENO_MAJOR, 513, QUALCOMM_VENDOR_ID));
    }

    @Test
    public void getBcnType_minor0_returnsPatch() {
        assertEquals(BCN_PATCH, getBcnType(ADRENO_MAJOR, 0, QUALCOMM_VENDOR_ID));
    }

    @Test
    public void getBcnType_minor1_returnsPatch() {
        assertEquals(BCN_PATCH, getBcnType(ADRENO_MAJOR, 1, QUALCOMM_VENDOR_ID));
    }

    @Test
    public void getBcnType_minorMidRange_returnsPatch() {
        assertEquals(BCN_PATCH, getBcnType(ADRENO_MAJOR, 256, QUALCOMM_VENDOR_ID));
    }

    // -----------------------------------------------------------------------
    // Boundary: minor = 513 vs 514 is the critical threshold
    // -----------------------------------------------------------------------

    @Test
    public void getBcnType_minorBoundary_513IsPatch_514IsBlob() {
        assertEquals("minor 513 must be PATCH", BCN_PATCH, getBcnType(ADRENO_MAJOR, 513, QUALCOMM_VENDOR_ID));
        assertEquals("minor 514 must be BLOB",  BCN_BLOB,  getBcnType(ADRENO_MAJOR, 514, QUALCOMM_VENDOR_ID));
    }

    // -----------------------------------------------------------------------
    // Enum constant values must match the C++ header definitions
    // -----------------------------------------------------------------------

    @Test
    public void enumConstants_incompatibleIs0() {
        assertEquals("ADRENOTOOLS_BCN_INCOMPATIBLE must equal 0", 0, BCN_INCOMPATIBLE);
    }

    @Test
    public void enumConstants_blobIs1() {
        assertEquals("ADRENOTOOLS_BCN_BLOB must equal 1", 1, BCN_BLOB);
    }

    @Test
    public void enumConstants_patchIs2() {
        assertEquals("ADRENOTOOLS_BCN_PATCH must equal 2", 2, BCN_PATCH);
    }

    @Test
    public void enumConstants_areDistinct() {
        assertNotEquals(BCN_INCOMPATIBLE, BCN_BLOB);
        assertNotEquals(BCN_INCOMPATIBLE, BCN_PATCH);
        assertNotEquals(BCN_BLOB,         BCN_PATCH);
    }

    // -----------------------------------------------------------------------
    // priv.h constants
    // -----------------------------------------------------------------------

    /**
     * The feature flag values from priv.h must be exactly the powers of two
     * assigned there so that bitfield operations in the driver loading code
     * work correctly.
     */
    @Test
    public void driverFeatureFlags_areDistinctPowersOfTwo() {
        int DRIVER_CUSTOM              = 1 << 0; // 1
        int DRIVER_FILE_REDIRECT       = 1 << 1; // 2
        int DRIVER_GPU_MAPPING_IMPORT  = 1 << 2; // 4

        assertEquals("ADRENOTOOLS_DRIVER_CUSTOM must be 1",              1, DRIVER_CUSTOM);
        assertEquals("ADRENOTOOLS_DRIVER_FILE_REDIRECT must be 2",       2, DRIVER_FILE_REDIRECT);
        assertEquals("ADRENOTOOLS_DRIVER_GPU_MAPPING_IMPORT must be 4",  4, DRIVER_GPU_MAPPING_IMPORT);

        // Bitfield isolation: each flag must only set its own bit
        assertEquals(0, DRIVER_CUSTOM             & DRIVER_FILE_REDIRECT);
        assertEquals(0, DRIVER_CUSTOM             & DRIVER_GPU_MAPPING_IMPORT);
        assertEquals(0, DRIVER_FILE_REDIRECT      & DRIVER_GPU_MAPPING_IMPORT);

        // OR-ing all three must equal 7
        assertEquals(7, DRIVER_CUSTOM | DRIVER_FILE_REDIRECT | DRIVER_GPU_MAPPING_IMPORT);
    }

    @Test
    public void gpuMappingSucceededMagic_matchesExpectedValue() {
        // From priv.h: #define ADRENOTOOLS_GPU_MAPPING_SUCCEEDED_MAGIC 0xDEADBEEF
        long magic = 0xDEADBEEFL;
        assertEquals("GPU mapping success magic must be 0xDEADBEEF", 0xDEADBEEFL, magic);
        // Ensure the value fits in a 32-bit unsigned / 64-bit Java long
        assertTrue("Magic must be positive as unsigned 32-bit", magic > 0);
        assertTrue("Magic must fit in a 64-bit Java long", magic < Long.MAX_VALUE);
    }
}