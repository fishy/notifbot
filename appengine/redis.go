package main

import (
	"context"

	"github.com/go-redis/redis"
)

var redisClient *redis.Client

func initRedis(ctx context.Context) error {
	redisURL, err := getSecret(ctx, redisID)
	if err != nil {
		return err
	}
	opt, err := redis.ParseURL(redisURL)
	if err != nil {
		return err
	}
	redisClient = redis.NewClient(opt)
	return nil
}

func isNotExist(err error) bool {
	return err == redis.Nil
}
