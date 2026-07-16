package com.github.mcmodderanchor.simplebedrockmodel.v1.common;

import com.maydaymemory.mae.basic.BaseKeyframe;

/**
 * 时间轴事件关键帧：某一时刻要执行的编译后事件 {@link TimelineEvent}。
 */
public class TimelineKeyframe extends BaseKeyframe<TimelineEvent> {
    private final TimelineEvent event;

    public TimelineKeyframe(float timeS, TimelineEvent event) {
        super(timeS);
        this.event = event;
    }

    @Override
    public TimelineEvent getValue() {
        return event;
    }
}
