package com.example.aitools.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aitools.workflow.entity.WorkflowRun;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRun> {
}
