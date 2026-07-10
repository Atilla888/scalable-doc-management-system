package de.hof.dms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Counter document backing EAP-number generation, stored in the MongoDB
 * {@code eap_sequences} collection. Each record holds the next sequence value
 * ({@code seq}) for a given {@code category}/{@code department}/{@code year}
 * combination, so unique EAP numbers can be allocated atomically.
 */
@Document(collection = "eap_sequences")
public class EapSequence {

    @Id
    private String id;

    private String category;

    private String department;

    private int year;

    private long seq;

    /** Returns the category component of the sequence key. */
    public String getCategory() {
        return category;
    }

    /** Sets the category component of the sequence key. */
    public void setCategory(String category) {
        this.category = category;
    }

    /** Returns the department component of the sequence key. */
    public String getDepartment() {
        return department;
    }

    /** Sets the department component of the sequence key. */
    public void setDepartment(String department) {
        this.department = department;
    }

    /** Returns the year component of the sequence key. */
    public int getYear() {
        return year;
    }

    /** Sets the year component of the sequence key. */
    public void setYear(int year) {
        this.year = year;
    }

    /** Returns the next sequence value to allocate. */
    public long getSeq() {
        return seq;
    }

    /** Sets the next sequence value to allocate. */
    public void setSeq(long seq) {
        this.seq = seq;
    }
}
