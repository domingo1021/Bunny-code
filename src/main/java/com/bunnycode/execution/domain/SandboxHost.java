package com.bunnycode.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/** A machine that runs sandbox containers. Adding capacity means inserting a row. */
@Entity
@Table(name = "host")
public class SandboxHost {

    @Id
    @Column(name = "ip_address", length = 15)
    private String address;

    protected SandboxHost() {
        // for JPA
    }

    public SandboxHost(String address) {
        this.address = Objects.requireNonNull(address, "address");
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SandboxHost that && address != null && address.equals(that.getAddress());
    }

    @Override
    public int hashCode() {
        return SandboxHost.class.hashCode();
    }

    @Override
    public String toString() {
        return "SandboxHost[" + address + "]";
    }
}
