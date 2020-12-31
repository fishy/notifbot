package main

import (
	"context"
	"sync/atomic"

	"github.com/go-redis/redis/v7"
)

var redisValue atomic.Value

func getRedis() (client *redis.Client, ok bool) {
	client, ok = redisValue.Load().(*redis.Client)
	return
}

func initRedis(ctx context.Context) {
	redisURL, err := getSecret(ctx, redisID)
	if err != nil {
		l(ctx).Errorw(
			"Failed to init redis secret",
			"err", err,
		)
		return
	}
	opt, err := redis.ParseURL(redisURL)
	if err != nil {
		l(ctx).Errorw(
			"Failed to parse redis url",
			"url", redisURL,
			"err", err,
		)
		return
	}
	redisValue.Store(redis.NewClient(opt))
}

func isNotExist(err error) bool {
	return err == redis.Nil
}
