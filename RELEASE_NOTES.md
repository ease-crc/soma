# Release Notes

## Upcoming (expected version is 2.0.0)
*   BREAKING CHANGE: Removal of `IOLite`
    <details>
        <summary>Details</summary>
        <ul>
            <li>
                Reason: incompatible with <code>DUL-v32</code>
            </li>
            <li>
                Note: Some important concepts, e.g. <code>IOLite#DigitalResource</code>, are still referenced in the taxonomy for backwards-compatibility. If you are missing any crucial concepts/roles, let us know
            </li>
            <li>
                Ongoing effort to create a model of CRAM (see next point) will replace some of the IOLite taxonomy at some point
            </li>
        </ul>
    </details>
*   BREAKING CHANGE: Complete and still ongoing remodeling of `SOMA-IO`
    <details>
        <summary>Details</summary>
        <ul>
            <li>
                Reason: Was in a primitive state; we need a better model of IO stuff to model CRAM
            </li>
            <li>
                Note: Removal/resorting of various concepts and roles. To the best of our knowledge, these should not have been in use anyway - please let us know if you are missing anything
            </li>
        </ul>
    </details>
*   BREAKING CHANGE: New ontology in the import structure: `SOMA NEEM`
    <details>
        <summary>Details</summary>
        Some SOMA entities, e.g., <code>KynoDinamicData</code>, have been decided to be too specific for the basic SOMA ontology. These have been moved to <code>SOMA NEEM</code>, which is of course part of the collapsed SOMA version
    </details>
*   Feat: Added mising labels to various concepts and roles
*   Feat: Metadata for Episodes, e.g., to represent a temporal context or a table setting
*   Feat: Protégé-setup for offline development
    <details>
        <summary>Details</summary>
        When opening SOMA locally from the cloned repository, Protégé will follow the IRI redirects in the catalog file from the same folder. There, we redirected the imports from the online version to the local version. This allows offline editing, while not changing anything when opening the ontologies via the IRI elsewhere
    </details>
*   Feat: We now host and use our own, modified copy of `DUL`
    <details>
        <summary>Details</summary>
        <ul>
            <li>
                Reason: <code>DUL</code> is unreliable (e.g., down for a whole week and no one can open <code>SOMA</code>)
            </li>
            <li>
                As a side effect, we can make changes to <code>DUL</code>, if necessary (yes, we will be very careful)
                <ul>
                    <li>
                        Removed italian lables
                    </li>
                    <li>
                        Added missing annotations of `rdfs:isDefinedIn` (only for <code>DUL</code> concepts / roles)
                    </li>
                    <li>
                        Removed unnecessary annotations of author and date
                    </li>
                    <li>
                        Added missing english labels
                    </li>
                </ul>
            </li>
        </ul>
    </details>
## 1.2.0
*   Base release for starting the changelog! Yay, now you get information why stuff might break!
    <details>
        <summary>Details</summary>
        The SOMA ontologies can now be accessed via a version IRI, e.g., <a href="http://www.ease-crc.org/ont/1.2.0/SOMA-STATE.owl">http://www.ease-crc.org/ont/1.2.0/SOMA-STATE.owl</a>. These are guaranteed to stay the way they are. The basic ontology IRI, e.g., <a href="http://www.ease-crc.org/ont/SOMA-STATE.owl">http://www.ease-crc.org/ont/SOMA-STATE.owl</a>, now refers to the latest version that is available (not neccessary associated to a stable release), and is referred to "Upcoming" at the top!
    </details>