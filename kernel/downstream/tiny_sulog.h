// SPDX-License-Identifier: GPL-2.0-only
/*
 * Copyright (C) 2026 \xx
 *
 * This file is a downstream extension and NOT affiliated, endorsed by,
 * or maintained by the official KernelSU developers.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 */

#ifndef __KSU_H_TINY_SULOG
#define __KSU_H_TINY_SULOG

// fast, lockless, partially inconsistent, half assed event-ringbuffer for su_compat
// more than good enough w/ all teh atomic drama happening

// 8 bytes
struct sulog_entry {
	uint32_t s_time;	// uptime in seconds
	uint32_t data;		// uint8_t[0,1,2] = uid, basically uint24_t, uint8_t[3] = symbol
} __attribute__((aligned(8)));

#define SULOG_ENTRY_MAX 250
#define SULOG_BUFSIZ SULOG_ENTRY_MAX * (sizeof (struct sulog_entry))

static void *sulog_buf_ptr = NULL;
static atomic_t sulog_index_next = ATOMIC_INIT(0);

static void tiny_sulog_init_heap()
{
	sulog_buf_ptr = kzalloc(SULOG_BUFSIZ, GFP_KERNEL);
	if (!sulog_buf_ptr)
		return;
	
	pr_info("sulog_init: allocated %lu bytes on 0x%lx \n", SULOG_BUFSIZ, (uintptr_t)sulog_buf_ptr);
}

/**
 *
 *  boottime_s_get, get kernel uptime in seconds
 *
 * - handles sub 4.10 compat
 * - we do this forced pointer cast to cut down on compat, pre 4.10, ktime is a union
 *
 * - bs handling 64-bit division on 32-bit (do_div)
 * - remainder = do_div(dividend, divisor); dividend will hold the quotient 
 * - for 64-bit we can straight up just use divide
 *
 */
static inline uint32_t boottime_s_get()
{
	ktime_t boottime_kt = ktime_get_boottime();

#ifdef CONFIG_64BIT 
	uint64_t boottime_s = *(uint64_t *)&boottime_kt / 1000000000;
#else
	uint64_t boottime_s = *(uint64_t *)&boottime_kt;
	do_div(boottime_s, 1000000000);
#endif

	return (uint32_t)boottime_s;
}

static noinline void write_sulog(uint8_t sym)
{
	if (!sulog_buf_ptr)
		return;

	struct sulog_entry entry = {0};

	// WARNING!!! this is LE only!
	entry.s_time = boottime_s_get();
	entry.data = (uint32_t)current_uid().val;
	*((char *)&entry.data + 3) = sym;

	// reserve slot
	uint32_t slot = atomic_read(&sulog_index_next);
	uint32_t next_slot;

retry:
	if (slot + 1 >= SULOG_ENTRY_MAX)
		next_slot = 0;
	else
		next_slot = slot + 1;

	// if sulog_index_next == slot, next_slot is written to it and old is retted
	// if sulog_index_next != slot, nothing happens and sulog_index_next is retted
	uint32_t curr_slot = atomic_cmpxchg_release(&sulog_index_next, slot, next_slot);
	if (curr_slot == slot)
		goto write;

	slot = curr_slot;
	goto retry;

write:
	// 64-bit is also atomic on armv7 via ldrexd + strexd, https://godbolt.org/z/7Tqnrcceq
	//__atomic_store((uint64_t *)sulog_buf_ptr + slot, (uint64_t *)&entry, __ATOMIC_RELEASE);
	atomic64_set((atomic64_t *)sulog_buf_ptr + slot, *(uint64_t *)&entry);
}

struct sulog_entry_rcv_ptr {
	uint64_t index_ptr; // send index here
	uint64_t buf_ptr; // send buf here
	uint64_t uptime_ptr; // uptime
};

static noinline int send_sulog_dump(void __user *uptr)
{
	if (!sulog_buf_ptr)
		return 1;

	struct sulog_entry_rcv_ptr sbuf = {0};

	if (copy_from_user(&sbuf, uptr, sizeof(sbuf) ))
		return 1;

	if (!sbuf.index_ptr || !sbuf.buf_ptr || !sbuf.uptime_ptr )
		return 1;

	// index can be a bit late but this doesnt matter in the grand scheme of things.
	// we'll take the discrepancy, its not as important anyway.
	void *memory __offstack(SULOG_BUFSIZ);
	if (!memory)
		return -ENOMEM;

	uint32_t uptime = boottime_s_get();
	uint32_t current_idx = (uint32_t)atomic_read_acquire(&sulog_index_next);
	memcpy(memory, sulog_buf_ptr, SULOG_BUFSIZ); // take a snapshot

	if (copy_to_user((void __user *)(uintptr_t)sbuf.uptime_ptr, &uptime, sizeof(uptime) ))
		return 1;

	if (copy_to_user((void __user *)(uintptr_t)sbuf.index_ptr, &current_idx, sizeof(current_idx) ))
		return 1;

	if (copy_to_user((void __user *)(uintptr_t)sbuf.buf_ptr, memory, SULOG_BUFSIZ ))
		return 1;

	return 0;
}

#endif // __KSU_H_TINY_SULOG
