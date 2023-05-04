package main

import (
	"context"
	"sync"
	"time"
)

const tickerTime = time.Second * 10

type chatCounterMapType map[int64]int64

type chatCounterType struct {
	m chatCounterMapType
	l sync.Mutex
}

func (c *chatCounterType) Get() chatCounterMapType {
	c.l.Lock()
	defer c.l.Unlock()

	var ret chatCounterMapType
	ret, c.m = c.m, make(chatCounterMapType)
	return ret
}

func (c *chatCounterType) Inc(id int64) {
	c.l.Lock()
	defer c.l.Unlock()

	c.m[id] = c.m[id] + 1
}

var chatCounter chatCounterType

func chatCounterMetricsLoop() {
	ctx := context.Background()
	chatCounter.Get()
	for range time.Tick(tickerTime) {
		data := chatCounter.Get()
		if err := sendMessageMetrics(ctx, data); err != nil {
			l(ctx).Error(
				"sendMessageMetrics failed",
				"err", err,
				"data", data,
			)
		}
	}
}
