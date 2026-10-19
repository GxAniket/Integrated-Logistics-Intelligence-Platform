import { Link, useLocation } from "react-router-dom";
import BusinessClientSidebar from "../components/BusinessClientSidebar";
import "./BusinessModule.css";

function BusinessModule() {
  const location = useLocation();

  const pageNames = {
    "/business/create-shipment": "Create Shipment",
    "/business/shipment-management": "Shipment Management",
    "/business/shipment-history": "Shipment History",
    "/business/package-information": "Package Information",
    "/business/tracking": "Tracking",
    "/business/delivery-performance": "Delivery Performance",
    "/business/delay-analysis": "Delay Analysis",
    "/business/logistics-overview": "Logistics Overview",
    "/business/customer-activity": "Customer Activity",
    "/business/reports": "Reports & Export",
    "/business/notifications": "Notifications",
  };

  const pageTitle =
    pageNames[location.pathname] || "Business Module";

  return (
    <div className="business-module-page">

      <BusinessClientSidebar />


      <main className="business-module-main">

        <header className="business-module-header">

          <div>

            <span>BUSINESS CLIENT</span>

            <h1>{pageTitle}</h1>

            <p>
              Business logistics module
            </p>

          </div>

          <div className="business-module-avatar">
            R
          </div>

        </header>


        <section className="business-module-card">

          <div className="business-module-icon">
            {pageTitle === "Tracking" ? "⌖" : "▣"}
          </div>

          <div>

            <span className="business-module-label">
              SHIPTRACK PRO
            </span>

            <h2>
              {pageTitle}
            </h2>

            <p>
              This Business Client module is connected
              and ready for the full frontend implementation.
            </p>

          </div>

        </section>


        <Link
          to="/dashboard/business"
          className="business-module-back"
        >
          ← Back to Business Dashboard
        </Link>

      </main>

    </div>
  );
}

export default BusinessModule;