package main

import (
	"context"
	"fmt"
	"log"
	"time"

	monitoring "cloud.google.com/go/monitoring/apiv3"
	googlepb "github.com/golang/protobuf/ptypes/timestamp"
	metricpb "google.golang.org/genproto/googleapis/api/metric"
	monitoredrespb "google.golang.org/genproto/googleapis/api/monitoredres"
	monitoringpb "google.golang.org/genproto/googleapis/monitoring/v3"
)

func sendMetrics(
	ctx context.Context,
	logger *log.Logger,
	client *monitoring.MetricClient,
	path string,
	point *monitoringpb.Point,
	labels map[string]string,
) error {
	// Writes time series data.
	if err := client.CreateTimeSeries(ctx, &monitoringpb.CreateTimeSeriesRequest{
		Name: monitoring.MetricProjectPath(getProjectID()),
		TimeSeries: []*monitoringpb.TimeSeries{
			{
				Metric: &metricpb.Metric{
					Type:   path,
					Labels: labels,
				},
				Resource: &monitoredrespb.MonitoredResource{
					Type: "global",
					Labels: map[string]string{
						"project_id": getProjectID(),
					},
				},
				Points: []*monitoringpb.Point{point},
			},
		},
	}); err != nil {
		return fmt.Errorf("metrics: failed to write time series data: %v", err)
	}

	return nil
}

func sendMessageMetrics(data chatCounterMapType, logger *log.Logger) error {
	if len(data) == 0 {
		return nil
	}

	start := time.Now()
	defer func() {
		logger.Printf(
			"sendMessageMetrics for %d took %v",
			len(data),
			time.Since(start),
		)
	}()

	ctx := context.Background()

	// Creates a client.
	client, err := monitoring.NewMetricClient(ctx)
	if err != nil {
		return fmt.Errorf("metrics: failed to create client: %v", err)
	}

	for id, count := range data {
		dataPoint := &monitoringpb.Point{
			Interval: &monitoringpb.TimeInterval{
				EndTime: &googlepb.Timestamp{
					Seconds: time.Now().Unix(),
				},
			},
			Value: &monitoringpb.TypedValue{
				Value: &monitoringpb.TypedValue_Int64Value{
					Int64Value: count,
				},
			},
		}

		if err := sendMetrics(
			ctx,
			logger,
			client,
			"custom.googleapis.com/messages/count",
			dataPoint,
			map[string]string{
				"chat_id": fmt.Sprintf("%d", id),
			},
		); err != nil {
			return err
		}
	}

	// Closes the client and flushes the data to Stackdriver.
	if err := client.Close(); err != nil {
		return fmt.Errorf("metrics: failed to close client: %v", err)
	}

	return nil
}
