package com.classroom.core.dto.file;

import com.classroom.core.model.PostFile;
import com.classroom.core.model.SolutionFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class FileDto {
    private UUID id;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private Instant uploadedAt;

    public static FileDto from(PostFile f) {
        return new FileDto(f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(), f.getUploadedAt());
    }

    public static FileDto from(SolutionFile f) {
        return new FileDto(f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(), f.getUploadedAt());
    }
}
