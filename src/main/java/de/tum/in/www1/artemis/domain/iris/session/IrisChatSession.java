package de.tum.in.www1.artemis.domain.iris.session;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import de.tum.in.www1.artemis.domain.User;

@Entity
public abstract class IrisChatSession extends IrisSession {

    @ManyToOne
    private User user;

    public IrisChatSession(User user) {
        this.user = user;
    }

    public IrisChatSession() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
