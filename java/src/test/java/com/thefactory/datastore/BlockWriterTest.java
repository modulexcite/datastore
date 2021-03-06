package com.thefactory.datastore;

import junit.framework.TestCase;

import static org.junit.Assert.assertArrayEquals;

public class BlockWriterTest extends TestCase {
    public TabletWriterOptions getTabletOptions() {
        TabletWriterOptions opts = new TabletWriterOptions();
        opts.blockSize = 4096;
        opts.keyRestartInterval = 10;
        opts.useCompression = false;
        return opts;
    }

    public void testWrite() throws Exception {
        BlockWriter tw = new BlockWriter(getTabletOptions());
        tw.append("foo".getBytes(), "bar".getBytes());

        byte[] out = tw.finish();

        assertEquals("foo", new String(tw.getFirstKey()));

        assertArrayEquals(out, new byte[]{
                0, -93, 102, 111, 111, -93, 98, 97, 114, /* "foo" -> "bar" */

                /* one restart @ 0x00000000 */
                0, 0, 0, 0,
                0, 0, 0, 1
        });

    }
}
