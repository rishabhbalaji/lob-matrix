package com.lobmatrix.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persisted relational metadata for a daily market ingestion session.
 */
@Entity
@Table(name = "session_metadata")
public class SessionMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false, unique = true)
    private LocalDate tradeDate;

    @Column(name = "feed_source", nullable = false)
    private String feedSource;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "total_raw_frames", nullable = false)
    private long totalRawFrames;

    @Column(name = "total_parquet_rows", nullable = false)
    private long totalParquetRows;

    @Column(name = "corrupted_frames_skipped", nullable = false)
    private long corruptedFramesSkipped;

    @Column(name = "status", nullable = false)
    private String status; // "IN_PROGRESS", "FINALIZED", "ERROR"

    public SessionMetadataEntity() {}

    public SessionMetadataEntity(LocalDate tradeDate, String feedSource, LocalDateTime startTime, 
                                 LocalDateTime endTime, long totalRawFrames, long totalParquetRows, 
                                 long corruptedFramesSkipped, String status) {
        this.tradeDate = tradeDate;
        this.feedSource = feedSource;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalRawFrames = totalRawFrames;
        this.totalParquetRows = totalParquetRows;
        this.corruptedFramesSkipped = corruptedFramesSkipped;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getFeedSource() { return feedSource; }
    public void setFeedSource(String feedSource) { this.feedSource = feedSource; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public long getTotalRawFrames() { return totalRawFrames; }
    public void setTotalRawFrames(long totalRawFrames) { this.totalRawFrames = totalRawFrames; }
    public long getTotalParquetRows() { return totalParquetRows; }
    public void setTotalParquetRows(long totalParquetRows) { this.totalParquetRows = totalParquetRows; }
    public long getCorruptedFramesSkipped() { return corruptedFramesSkipped; }
    public void setCorruptedFramesSkipped(long corruptedFramesSkipped) { this.corruptedFramesSkipped = corruptedFramesSkipped; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
