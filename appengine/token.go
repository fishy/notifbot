package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync/atomic"
	"time"
)

const (
	urlPrefix           = "https://api.telegram.org/bot"
	webhookMaxConn      = 5
	postFormContentType = "application/x-www-form-urlencoded"
)

var tokenValue atomic.Value

var httpClient http.Client

type telegramToken struct {
	Token string
}

func (bot *telegramToken) String() string {
	return bot.Token
}

func (bot *telegramToken) getURL(endpoint string) string {
	return fmt.Sprintf("%s%s/%s", urlPrefix, bot.String(), endpoint)
}

// PostRequest use POST method to send a request to telegram
func (bot *telegramToken) PostRequest(
	ctx context.Context, endpoint string, params url.Values,
) (code int) {
	start := time.Now()
	defer func() {
		l(ctx).Infow(
			"PostRequest: HTTP POST done",
			"endpoint", endpoint,
			"took", time.Since(start),
		)
	}()

	req, err := http.NewRequest(
		http.MethodPost,
		bot.getURL(endpoint),
		strings.NewReader(params.Encode()),
	)
	if err != nil {
		l(ctx).Errorw(
			"Failed to construct http request",
			"err", err,
		)
		return
	}
	req.Header.Set("Content-Type", postFormContentType)
	resp, err := httpClient.Do(req.WithContext(ctx))
	if resp != nil && resp.Body != nil {
		defer DrainAndClose(resp.Body)
	}
	if err != nil {
		l(ctx).Errorw(
			"PostRequest failed",
			"endpoint", endpoint,
			"err", err,
		)
		return
	}
	if resp.StatusCode != http.StatusOK {
		buf, _ := io.ReadAll(resp.Body)
		l(ctx).Errorw(
			"PostRequest got non-200",
			"endpoint", endpoint,
			"code", resp.StatusCode,
			"body", buf,
		)
	}
	return resp.StatusCode
}

// SendMessage sents a telegram messsage.
func (bot *telegramToken) SendMessage(
	ctx context.Context, id int64, msg string,
) int {
	values := url.Values{}
	values.Add("chat_id", fmt.Sprintf("%d", id))
	values.Add("text", msg)
	return bot.PostRequest(ctx, "sendMessage", values)
}

// initBot initializes botToken.
func initBot(ctx context.Context) {
	secret, err := getSecret(ctx, tokenID)
	if err != nil {
		l(ctx).Errorw(
			"Failed to get token secret",
			"err", err,
		)
	}
	tokenValue.Store(&telegramToken{
		Token: secret,
	})
}

func getToken() *telegramToken {
	return tokenValue.Load().(*telegramToken)
}
