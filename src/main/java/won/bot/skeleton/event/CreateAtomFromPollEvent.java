package won.bot.skeleton.event;

import won.bot.framework.eventbot.event.BaseEvent;
import won.bot.skeleton.model.Poll;

public class CreateAtomFromPollEvent extends BaseEvent {
    private final Poll poll;

    public CreateAtomFromPollEvent(Poll poll) {
        this.poll = poll;
    }

    public Poll getPoll() {
        return poll;
    }
}
