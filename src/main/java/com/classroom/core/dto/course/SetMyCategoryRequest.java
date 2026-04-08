package com.classroom.core.dto.course;

import lombok.Data;

import java.util.UUID;

@Data
public class SetMyCategoryRequest {
    private UUID categoryId;
}