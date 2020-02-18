package main

import (
	"context"
	"sync/atomic"

	"github.com/go-redis/redis"
)

var redisValue atomic.Value

func getRedis() (client *redis.Client, ok bool) {
	client, ok = redisValue.Load().(*redis.Client)
	return
}

func initRedis(ctx context.Context) {
	redisURL, err := getSecret(ctx, redisID)
	if err != nil {
		errorLog.Printf("Failed to init redis secret: %v", err)
		return
	}
	opt, err := redis.ParseURL(redisURL)
	if err != nil {
		errorLog.Printf("Failed to parse redis url %q: %v", redisURL, err)
		return
	}
	redisValue.Store(redis.NewClient(opt))
}

func isNotExist(err error) bool {
	return err == redis.Nil
}
