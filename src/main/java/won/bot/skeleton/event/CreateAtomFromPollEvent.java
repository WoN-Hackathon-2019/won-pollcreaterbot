package won.bot.skeleton.event;

import won.bot.framework.eventbot.event.BaseEvent;
import won.bot.skeleton.strawpoll.api.models.SPPoll;


public class CreateAtomFromPollEvent extends BaseEvent {
    private final SPPoll poll;

    public CreateAtomFromPollEvent(SPPoll poll) {
        this.poll = poll;
    }

    public SPPoll getPoll() {
        return poll;
    }
}
