package datastore

import (
	"bytes"
	"encoding/binary"
)

type BlockWriter struct {
	opts     *TabletOptions
	buf      *bytes.Buffer
	count    uint
	restarts []uint32
	firstKey []byte
	prevKey  []byte
}

func NewBlockWriter(opts *TabletOptions) *BlockWriter {
	// initialize buf to 2*BlockSize to minimize resizes
	buf := bytes.NewBuffer(make([]byte, 0, 2*opts.BlockSize))
	return &BlockWriter{opts: opts, buf: buf}
}

// number of bytes of prefix in common between two byte slices
func commonPrefix(bin1 []byte, bin2 []byte) uint {
	length := len(bin1)
	if len(bin2) < length {
		length = len(bin2)
	}

	var count uint
	for i := 0; i < length; i++ {
		if bin1[i] == bin2[i] {
			count++
		} else {
			break
		}
	}

	return count
}

func (b *BlockWriter) Append(key []byte, value []byte) {
	if b.buf.Len() == 0 {
		// Make a copy of firstKey, since it may be coming
		// from memory we don't own. We could also maintain a
		// reference into our buf, but needs to be offset &
		// length because buf is resized dynamically.
		b.firstKey = make([]byte, len(key))
		copy(b.firstKey, key)
	}

	var shared uint
	if b.count%b.opts.KeyRestartInterval == 0 {
		b.restarts = append(b.restarts, uint32(b.buf.Len()))
	} else {
		shared = commonPrefix(b.prevKey, key)
	}

	writeUint(b.buf, shared)
	writeKv(b.buf, key[shared:len(key)], value)
	b.prevKey = key
	b.count += 1
}

func (b *BlockWriter) Size() uint32 {
	return uint32(b.buf.Len() + 4*len(b.restarts) + 4)
}

func (b *BlockWriter) Finish() (firstKey []byte, buf []byte) {
	for _, restart := range b.restarts {
		binary.Write(b.buf, binary.BigEndian, restart)
	}

	binary.Write(b.buf, binary.BigEndian, uint32(len(b.restarts)))

	return b.firstKey, b.buf.Bytes()
}

func (b *BlockWriter) Reset() {
	b.buf.Reset()
	b.count = 0
	b.restarts = nil
	b.firstKey = nil
	b.prevKey = nil
}
