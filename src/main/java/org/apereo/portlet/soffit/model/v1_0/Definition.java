/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apereo.portlet.soffit.model.v1_0;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides information about the publication record of the soffit within the
 * portal.
 *
 * @author drewwills
 */
public class Definition {

    private String title;
    private String fname;
    private String description;

    private Set<String> categories = new HashSet<>();
    private Map<String,List<String>> parameters = new HashMap<>();
    private Map<String,List<String>> preferences = new HashMap<>();

    public String getTitle() {
        return title;
    }

    public Definition setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getFname() {
        return fname;
    }

    public Definition setFname(String fname) {
        this.fname = fname;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Definition setDescription(String description) {
        this.description = description;
        return this;
    }

    public Set<String> getCategories() {
        // Defensive copy
        return Collections.unmodifiableSet(categories);
    }

    public void addCategory(String category) {
        categories.add(category);
    }

    public boolean removeCategory(String category) {
        return categories.remove(category);
    }

    public Map<String, List<String>> getParameters() {
        // Defensive copy
        return Collections.unmodifiableMap(parameters);
    }

    public List<String> removeParameter(String key) {
        return parameters.remove(key);
    }

    public void setParameter(String key, List<String> values) {
        parameters.put(key, values);
    }

    public Map<String, List<String>> getPreferences() {
        // Defensive copy
        return Collections.unmodifiableMap(preferences);
    }

    public List<String> removePreference(String key) {
        return preferences.remove(key);
    }

    public void setPreference(String key, List<String> values) {
        preferences.put(key, values);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((categories == null) ? 0 : categories.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((fname == null) ? 0 : fname.hashCode());
        result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + ((preferences == null) ? 0 : preferences.hashCode());
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Definition other = (Definition) obj;
        if (categories == null) {
            if (other.categories != null)
                return false;
        } else if (!categories.equals(other.categories))
            return false;
        if (description == null) {
            if (other.description != null)
                return false;
        } else if (!description.equals(other.description))
            return false;
        if (fname == null) {
            if (other.fname != null)
                return false;
        } else if (!fname.equals(other.fname))
            return false;
        if (parameters == null) {
            if (other.parameters != null)
                return false;
        } else if (!parameters.equals(other.parameters))
            return false;
        if (preferences == null) {
            if (other.preferences != null)
                return false;
        } else if (!preferences.equals(other.preferences))
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Definition [title=" + title + ", fname=" + fname + ", description=" + description + ", categories="
                + categories + ", parameters=" + parameters + ", preferences=" + preferences + "]";
    }

}
