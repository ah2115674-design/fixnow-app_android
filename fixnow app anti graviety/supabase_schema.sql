-- ====================================================================
-- FIXNOW HIGH-PERFORMANCE SUPABASE PRODUCTION SCHEMA (RECREATION READY)
-- ====================================================================
-- Designed for enterprise-scale: 100,000+ users, 10,000+ technicians,
-- and millions of bookings with strong consistency and auto-scaling indexes.
-- ====================================================================

-- ----------------------------------------------------
-- 0. CLEANUP EXISTING OLD SCHEMAS & DEPENDENTS
-- ----------------------------------------------------
DROP VIEW IF EXISTS public.booking_activity_view CASCADE;

DROP TABLE IF EXISTS public.audit_logs CASCADE;
DROP TABLE IF EXISTS public.admin_settings CASCADE;
DROP TABLE IF EXISTS public.technician_services CASCADE;
DROP TABLE IF EXISTS public.services CASCADE;
DROP TABLE IF EXISTS public.service_categories CASCADE;
DROP TABLE IF EXISTS public.earnings CASCADE;
DROP TABLE IF EXISTS public.payouts CASCADE;
DROP TABLE IF EXISTS public.payments CASCADE;
DROP TABLE IF EXISTS public.disputes CASCADE;
DROP TABLE IF EXISTS public.reviews CASCADE;
DROP TABLE IF EXISTS public.notifications CASCADE;
DROP TABLE IF EXISTS public.chat_messages CASCADE;
DROP TABLE IF EXISTS public.chat_rooms CASCADE;
DROP TABLE IF EXISTS public.technician_locations CASCADE;
DROP TABLE IF EXISTS public.booking_photos CASCADE;
DROP TABLE IF EXISTS public.booking_status_history CASCADE;
DROP TABLE IF EXISTS public.bookings CASCADE;
DROP TABLE IF EXISTS public.technician_verification CASCADE;
DROP TABLE IF EXISTS public.technician_documents CASCADE;
DROP TABLE IF EXISTS public.admins CASCADE;
DROP TABLE IF EXISTS public.technicians CASCADE;
DROP TABLE IF EXISTS public.customers CASCADE;
DROP TABLE IF EXISTS public.profiles CASCADE;

-- ----------------------------------------------------
-- 1. EXTENSIONS & PREREQUISITES
-- ----------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Define Roles Custom Enum
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE public.user_role AS ENUM ('customer', 'technician', 'admin');
    END IF;
END $$;

-- Define Document Types Enum
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'doc_type') THEN
        CREATE TYPE public.doc_type AS ENUM ('national_id', 'driving_license', 'police_clearance', 'trade_certificate');
    END IF;
END $$;

-- Define Verification Status Enum
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'verification_status') THEN
        CREATE TYPE public.verification_status AS ENUM ('pending', 'approved', 'rejected');
    END IF;
END $$;

-- Define Booking Status Enum
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status') THEN
        CREATE TYPE public.booking_status AS ENUM (
            'Requested', 
            'Assigned', 
            'Technician En Route', 
            'Arrived', 
            'In Progress', 
            'Completed', 
            'Cancelled'
        );
    END IF;
END $$;

-- ----------------------------------------------------
-- 2. DATABASE TABLES (WITH RELATIONSHIPS & CONSTRAINTS)
-- ----------------------------------------------------

-- profiles: Root user account profile mapped directly to auth.users
CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    role public.user_role NOT NULL DEFAULT 'customer',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- customers: Customer-specific information
CREATE TABLE public.customers (
    id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    phone TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    city TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- technicians: Technician details, real-time location metrics & levels
CREATE TABLE public.technicians (
    id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    phone TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    sub_category TEXT,
    city TEXT NOT NULL,
    cnic TEXT NOT NULL,
    selfie_url TEXT,
    bank_details TEXT,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    rating DOUBLE PRECISION NOT NULL DEFAULT 4.5 CHECK (rating BETWEEN 0.0 AND 5.0),
    total_jobs INT NOT NULL DEFAULT 0 CHECK (total_jobs >= 0),
    acceptance_rate DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (acceptance_rate BETWEEN 0.0 AND 1.0),
    latitude DOUBLE PRECISION NOT NULL DEFAULT 31.5204,
    longitude DOUBLE PRECISION NOT NULL DEFAULT 74.3587,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- admins: Back-office administration users
CREATE TABLE public.admins (
    id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    phone TEXT,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- technician_documents: Official documents uploaded for vetting
CREATE TABLE public.technician_documents (
    id BIGSERIAL PRIMARY KEY,
    technician_id UUID NOT NULL REFERENCES public.technicians(id) ON DELETE CASCADE,
    document_type public.doc_type NOT NULL,
    document_url TEXT NOT NULL,
    status public.verification_status NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- technician_verification: Audit list for vetting & background check review logs
CREATE TABLE public.technician_verification (
    id BIGSERIAL PRIMARY KEY,
    technician_id UUID NOT NULL REFERENCES public.technicians(id) ON DELETE CASCADE,
    verified_by UUID REFERENCES public.admins(id) ON DELETE SET NULL,
    status public.verification_status NOT NULL DEFAULT 'pending',
    rejection_reason TEXT,
    notes TEXT,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- bookings: Ultimate system booking ledger
CREATE TABLE public.bookings (
    id BIGSERIAL PRIMARY KEY,
    service_category TEXT NOT NULL,
    service_name TEXT NOT NULL,
    issue_description TEXT,
    customer_id UUID NOT NULL REFERENCES public.customers(id),
    customer_phone TEXT NOT NULL,
    customer_name TEXT NOT NULL,
    customer_address TEXT NOT NULL,
    customer_city TEXT NOT NULL,
    preferred_time TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    price DOUBLE PRECISION NOT NULL CHECK (price >= 0),
    status public.booking_status NOT NULL DEFAULT 'Requested',
    technician_id UUID REFERENCES public.technicians(id) ON DELETE SET NULL,
    technician_phone TEXT,
    technician_name TEXT,
    rating INT DEFAULT 0 CHECK (rating BETWEEN 0 AND 5),
    review_comment TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    tech_latitude DOUBLE PRECISION NOT NULL DEFAULT 31.5204,
    tech_longitude DOUBLE PRECISION NOT NULL DEFAULT 74.3587,
    declined_technicians TEXT DEFAULT '',
    is_manual_assign BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- booking_status_history: Live tracking status transitions (for SLA & timeline)
CREATE TABLE public.booking_status_history (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES public.bookings(id) ON DELETE CASCADE,
    status public.booking_status NOT NULL,
    changed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- booking_photos: Work progress photos (before vs. after proof of service)
CREATE TABLE public.booking_photos (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES public.bookings(id) ON DELETE CASCADE,
    photo_url TEXT NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES public.profiles(id),
    phase TEXT NOT NULL CHECK (phase IN ('before', 'after')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- technician_locations: Dedicated ultra-fast GPS telemetry stream index
CREATE TABLE public.technician_locations (
    technician_id UUID PRIMARY KEY REFERENCES public.technicians(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- chat_rooms: Channels of text correspondence between client & dispatcher/tech
CREATE TABLE public.chat_rooms (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES public.bookings(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
    technician_id UUID NOT NULL REFERENCES public.technicians(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_chat_room UNIQUE (booking_id, customer_id, technician_id)
);

-- chat_messages: Customer & technician chat messages database
CREATE TABLE public.chat_messages (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES public.chat_rooms(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE SET NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- notifications: Notifications center database records
CREATE TABLE public.notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- reviews: Quality feedback and ratings table
CREATE TABLE public.reviews (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES public.bookings(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
    technician_id UUID NOT NULL REFERENCES public.technicians(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- disputes: Escalation and administrative dispute handling records
CREATE TABLE public.disputes (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES public.bookings(id) ON DELETE CASCADE,
    raised_by UUID NOT NULL REFERENCES public.profiles(id),
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'under_investigation', 'resolved', 'closed')),
    resolution_notes TEXT,
    resolved_by UUID REFERENCES public.admins(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- payments: System billing transactions
CREATE TABLE public.payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES public.bookings(id) ON DELETE CASCADE,
    amount DOUBLE PRECISION NOT NULL CHECK (amount >= 0),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'successful', 'failed', 'refunded')),
    payment_method TEXT NOT NULL,
    transaction_reference TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- payouts: Technician banking disbursements ledger
CREATE TABLE public.payouts (
    id BIGSERIAL PRIMARY KEY,
    technician_id UUID NOT NULL REFERENCES public.technicians(id) ON DELETE CASCADE,
    amount DOUBLE PRECISION NOT NULL CHECK (amount >= 0),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('requested', 'pending', 'completed', 'rejected')),
    bank_transaction_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- earnings: Revenue shares per completed transaction
CREATE TABLE public.earnings (
    id BIGSERIAL PRIMARY KEY,
    technician_id UUID NOT NULL REFERENCES public.technicians(id) ON DELETE CASCADE,
    technician_phone TEXT NOT NULL,
    booking_id BIGINT NOT NULL REFERENCES public.bookings(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- service_categories: High level domain categories (Plumbing, HVAC, Electrical)
CREATE TABLE public.service_categories (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- services: Operational catalogs/items with baseline pricing structures
CREATE TABLE public.services (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES public.service_categories(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    base_price DOUBLE PRECISION NOT NULL CHECK (base_price >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- technician_services: Map technicians to categories of specialization
CREATE TABLE public.technician_services (
    technician_id UUID NOT NULL REFERENCES public.technicians(id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES public.services(id) ON DELETE CASCADE,
    PRIMARY KEY (technician_id, service_id)
);

-- admin_settings: Central core variables & dynamic operational controls
CREATE TABLE public.admin_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- audit_logs: Dynamic back-office changes tracker for forensic reporting
CREATE TABLE public.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES public.profiles(id),
    action TEXT NOT NULL,
    target_table TEXT NOT NULL,
    target_row_id TEXT,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------
-- 3. PERFORMANCE OPTIMIZATIONS (INDEXES DEFINED)
-- ----------------------------------------------------

-- Indexing for Booking Status (Used in feeds, state-triggers)
CREATE INDEX IF NOT EXISTS idx_bookings_status ON public.bookings(status);

-- Structural Compound Index for Quick Dispatching (Find Active Dispatchable Technicians in specific city)
CREATE INDEX IF NOT EXISTS idx_tech_availability ON public.technicians(city, is_online, is_approved);

-- Indexing for Coordinates (Real-time GIS matching lookup)
CREATE INDEX IF NOT EXISTS idx_bookings_coordinates ON public.bookings(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_tech_coordinates ON public.technicians(latitude, longitude);

-- Fast primary key indices references to speed up joins containing massive volumes
CREATE INDEX IF NOT EXISTS idx_bookings_cust_id ON public.bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_tech_id ON public.bookings(technician_id);

CREATE INDEX IF NOT EXISTS idx_chat_msgs_room_id ON public.chat_messages(room_id);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON public.notifications(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON public.notifications(is_read);

-- Ordering & feed index targets
CREATE INDEX IF NOT EXISTS idx_bookings_created_at ON public.bookings(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_msgs_created_at ON public.chat_messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON public.audit_logs(created_at DESC);

-- ----------------------------------------------------
-- 4. TRIGGERS & SYSTEMS DESIGN
-- ----------------------------------------------------

-- Keep updated_at Timestamp fresh
CREATE OR REPLACE FUNCTION public.update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply automatic timestamp updates to tables
CREATE TRIGGER update_profiles_modtime BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.update_modified_column();
CREATE TRIGGER update_customers_modtime BEFORE UPDATE ON public.customers FOR EACH ROW EXECUTE FUNCTION public.update_modified_column();
CREATE TRIGGER update_technicians_modtime BEFORE UPDATE ON public.technicians FOR EACH ROW EXECUTE FUNCTION public.update_modified_column();
CREATE TRIGGER update_admins_modtime BEFORE UPDATE ON public.admins FOR EACH ROW EXECUTE FUNCTION public.update_modified_column();
CREATE TRIGGER update_bookings_modtime BEFORE UPDATE ON public.bookings FOR EACH ROW EXECUTE FUNCTION public.update_modified_column();
CREATE TRIGGER update_payouts_modtime BEFORE UPDATE ON public.payouts FOR EACH ROW EXECUTE FUNCTION public.update_modified_column();
CREATE TRIGGER update_disputes_modtime BEFORE UPDATE ON public.disputes FOR EACH ROW EXECUTE FUNCTION public.update_modified_column();

-- Trigger function to update booking history upon any status update
CREATE OR REPLACE FUNCTION public.track_booking_status_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.status IS NULL OR OLD.status != NEW.status) THEN
        INSERT INTO public.booking_status_history(booking_id, status, changed_by, notes)
        VALUES (
            NEW.id, 
            NEW.status, 
            COALESCE(NEW.technician_id, NEW.customer_id), 
            'Status automated log shift to ' || NEW.status::text
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_booking_status_history
    AFTER UPDATE OF status ON public.bookings
    FOR EACH ROW EXECUTE FUNCTION public.track_booking_status_changes();

-- Sync real-time location stream index upon technician core coordinates update
CREATE OR REPLACE FUNCTION public.sync_realtime_tech_coordinates()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.technician_locations (technician_id, latitude, longitude, updated_at)
    VALUES (NEW.id, NEW.latitude, NEW.longitude, NOW())
    ON CONFLICT (technician_id) DO UPDATE 
    SET latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sync_tech_location
    AFTER INSERT OR UPDATE OF latitude, longitude ON public.technicians
    FOR EACH ROW EXECUTE FUNCTION public.sync_realtime_tech_coordinates();

-- ----------------------------------------------------
-- 5. PROFILE GENERATION FROM SUPABASE AUTH SYSTEM
-- ----------------------------------------------------

-- Synchronizes Supabase Auth signups perfectly into operational db profiles/tables
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_role public.user_role;
    v_name TEXT;
    v_phone TEXT;
    v_city TEXT;
    v_category TEXT;
    v_cnic TEXT;
BEGIN
    -- Extract registration variables from auth metadata
    v_role := COALESCE((NEW.raw_user_meta_data->>'role')::public.user_role, 'customer'::public.user_role);
    v_name := COALESCE(NEW.raw_user_meta_data->>'name', 'User ' || SUBSTRING(NEW.email FROM 1 FOR POSITION('@' IN NEW.email)-1));
    v_phone := COALESCE(NEW.raw_user_meta_data->>'phone', '+92' || SUBSTRING(MD5(RANDOM()::text) FROM 1 FOR 9));
    v_city := COALESCE(NEW.raw_user_meta_data->>'city', 'Lahore');
    v_category := COALESCE(NEW.raw_user_meta_data->>'category', 'General');
    v_cnic := COALESCE(NEW.raw_user_meta_data->>'cnic', '00000-0000000-0');

    -- Create profile link
    INSERT INTO public.profiles (id, email, role)
    VALUES (NEW.id, NEW.email, v_role);

    -- Segment specific details based on Role Definition
    IF v_role = 'customer' THEN
        INSERT INTO public.customers (id, phone, name, email, city)
        VALUES (NEW.id, v_phone, v_name, NEW.email, v_city);
    ELSIF v_role = 'technician' THEN
        INSERT INTO public.technicians (id, phone, name, category, city, cnic, is_approved)
        VALUES (NEW.id, v_phone, v_name, v_category, v_city, v_cnic, FALSE);
    ELSIF v_role = 'admin' THEN
        INSERT INTO public.admins (id, phone, name)
        VALUES (NEW.id, v_phone, v_name);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Map authentication signups
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ----------------------------------------------------
-- 6. ATOMIC TRANSITIONS (RACE-CONDITION FREE BOOKING)
-- ----------------------------------------------------

-- Protects dispatch routing. Ensures single-execution reservation.
CREATE OR REPLACE FUNCTION public.accept_booking(
    p_booking_id BIGINT,
    p_technician_id UUID
) RETURNS BOOLEAN AS $$
DECLARE
    v_status public.booking_status;
    v_tech_name TEXT;
    v_tech_phone TEXT;
    v_tech_approved BOOLEAN;
BEGIN
    -- 1. Check if dispatcher/rider is approved
    SELECT name, phone, is_approved INTO v_tech_name, v_tech_phone, v_tech_approved
    FROM public.technicians
    WHERE id = p_technician_id;

    IF NOT v_tech_approved THEN
        RAISE EXCEPTION 'Vetting Process Incomplete. This technician is not approved to accept jobs.';
    END IF;

    -- 2. Obtain high durability transaction lock (SELECT FOR UPDATE)
    SELECT status INTO v_status
    FROM public.bookings
    WHERE id = p_booking_id
    FOR UPDATE;

    -- Check if booking is available and unclaimed
    IF v_status IS NULL OR v_status != 'Requested' THEN
        RETURN FALSE; -- Already claimed by another technician, prevents race conditions
    END IF;

    -- 3. Transition booking atomically
    UPDATE public.bookings
    SET status = 'Assigned',
        technician_id = p_technician_id,
        technician_phone = v_tech_phone,
        technician_name = v_tech_name,
        updated_at = NOW()
    WHERE id = p_booking_id;

    -- 4. Insert transition log
    INSERT INTO public.booking_status_history(booking_id, status, changed_by, notes)
    VALUES (p_booking_id, 'Assigned', p_technician_id, 'Technician ' || v_tech_name || ' claimed request successfully.');

    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Find nearby online and approved technicians for dispatcher matching
CREATE OR REPLACE FUNCTION public.find_nearby_technicians(
    p_booking_latitude DOUBLE PRECISION,
    p_booking_longitude DOUBLE PRECISION,
    p_city TEXT,
    p_category TEXT
) RETURNS TABLE (
    id UUID,
    phone TEXT,
    name TEXT,
    rating DOUBLE PRECISION,
    distance DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.id,
        t.phone,
        t.name,
        t.rating,
        (sqrt((t.latitude - p_booking_latitude)^2 + (t.longitude - p_booking_longitude)^2) * 100.0) AS distance
    FROM 
        public.technicians t
    WHERE 
        t.city = p_city
        AND t.category = p_category
        AND t.is_online = TRUE
        AND t.is_approved = TRUE
    ORDER BY 
        distance ASC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ----------------------------------------------------
-- 7. SECURITY STRUCTURES (ROW LEVEL SECURITY POLICIES)
-- ----------------------------------------------------

-- Enable Row Level Security (RLS) on all profiles & transactions tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.technicians ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admins ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.technician_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.technician_verification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.booking_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.booking_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.technician_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.disputes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.earnings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.service_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.services ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.technician_services ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- Helper security role functions bypass
CREATE OR REPLACE FUNCTION public.get_auth_role()
RETURNS public.user_role AS $$
    SELECT role FROM public.profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER;

-- A. Profiles RLS POLICIES
CREATE POLICY "Allow individual read of own profile" ON public.profiles FOR SELECT USING (id = auth.uid());
CREATE POLICY "Allow individual update of own profile" ON public.profiles FOR UPDATE USING (id = auth.uid());
CREATE POLICY "Admins full override access Profiles" ON public.profiles FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- B. Customers RLS POLICIES
CREATE POLICY "Customers view own details" ON public.customers FOR SELECT USING (id = auth.uid());
CREATE POLICY "Customers update own details" ON public.customers FOR UPDATE USING (id = auth.uid());
CREATE POLICY "Technicians of booking view customer detail" ON public.customers FOR SELECT USING (
    EXISTS (
        SELECT 1 FROM public.bookings b 
        WHERE b.customer_id = public.customers.id AND b.technician_id = auth.uid()
    )
);
CREATE POLICY "Admins full override access Customers" ON public.customers FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- C. Technicians RLS POLICIES
CREATE POLICY "Public read of dynamic tech listings" ON public.technicians FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "Technician update own profile" ON public.technicians FOR UPDATE USING (id = auth.uid());
CREATE POLICY "Admins full override access Technicians" ON public.technicians FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- D. Admins RLS POLICIES
CREATE POLICY "Admins read administrative profiles" ON public.admins FOR SELECT USING (auth.uid() = id OR public.get_auth_role() = 'admin');
CREATE POLICY "Admins full override access Admins Table" ON public.admins FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- E. Technician Documents RLS POLICIES
CREATE POLICY "Technician view own documents" ON public.technician_documents FOR SELECT USING (technician_id = auth.uid());
CREATE POLICY "Technician upload document" ON public.technician_documents FOR INSERT TO authenticated WITH CHECK (technician_id = auth.uid());
CREATE POLICY "Admins full override access Documents" ON public.technician_documents FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- F. Technician Verification RLS POLICIES
CREATE POLICY "Technicians view own verification audits" ON public.technician_verification FOR SELECT USING (technician_id = auth.uid());
CREATE POLICY "Admins full override access Verification" ON public.technician_verification FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- G. Bookings RLS POLICIES
CREATE POLICY "Customers view own bookings" ON public.bookings FOR SELECT USING (customer_id = auth.uid());
CREATE POLICY "Customers insert bookings" ON public.bookings FOR INSERT TO authenticated WITH CHECK (customer_id = auth.uid());
CREATE POLICY "Customers update own specific job request" ON public.bookings FOR UPDATE USING (customer_id = auth.uid());
CREATE POLICY "Technicians view matched bookings/feed" ON public.bookings FOR SELECT TO authenticated USING (
    technician_id = auth.uid() OR (status = 'Requested' AND customer_city = (SELECT city FROM public.technicians WHERE id = auth.uid()))
);
CREATE POLICY "Technicians update matched bookings" ON public.bookings FOR UPDATE USING (technician_id = auth.uid());
CREATE POLICY "Admins full override access Bookings" ON public.bookings FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- H. Booking Status History RLS POLICIES
CREATE POLICY "Participants read status changes" ON public.booking_status_history FOR SELECT TO authenticated USING (
    EXISTS (
        SELECT 1 FROM public.bookings b 
        WHERE b.id = booking_id AND (b.customer_id = auth.uid() OR b.technician_id = auth.uid())
    )
);
CREATE POLICY "Admins full override access Status Changes" ON public.booking_status_history FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- I. Booking Photos RLS POLICIES
CREATE POLICY "Participants read booking progress photos" ON public.booking_photos FOR SELECT TO authenticated USING (
    EXISTS (
        SELECT 1 FROM public.bookings b 
        WHERE b.id = booking_id AND (b.customer_id = auth.uid() OR b.technician_id = auth.uid())
    )
);
CREATE POLICY "Participants upload progress photos" ON public.booking_photos FOR INSERT TO authenticated WITH CHECK (uploaded_by = auth.uid());
CREATE POLICY "Admins full override access Photos" ON public.booking_photos FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- J. Technician Locations RLS POLICIES
CREATE POLICY "Authorized clients tracking technical streams" ON public.technician_locations FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "Technician updates location coordinates" ON public.technician_locations FOR ALL TO authenticated USING (technician_id = auth.uid()) WITH CHECK (technician_id = auth.uid());
CREATE POLICY "Admins full override access Locations" ON public.technician_locations FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- K. Chat Rooms RLS POLICIES
CREATE POLICY "Participants read chat rooms" ON public.chat_rooms FOR SELECT TO authenticated USING (customer_id = auth.uid() OR technician_id = auth.uid());
CREATE POLICY "Participants insert chat rooms" ON public.chat_rooms FOR INSERT TO authenticated WITH CHECK (customer_id = auth.uid() OR technician_id = auth.uid());
CREATE POLICY "Admins full override access Chat Rooms" ON public.chat_rooms FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- L. Chat Messages RLS POLICIES
CREATE POLICY "Participants read chat messages" ON public.chat_messages FOR SELECT TO authenticated USING (
    EXISTS (
        SELECT 1 FROM public.chat_rooms r 
        WHERE r.id = room_id AND (r.customer_id = auth.uid() OR r.technician_id = auth.uid())
    )
);
CREATE POLICY "Participants insert chat messages" ON public.chat_messages FOR INSERT TO authenticated WITH CHECK (sender_id = auth.uid());
CREATE POLICY "Admins full override access Chat Messages" ON public.chat_messages FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- M. Notifications RLS POLICIES
CREATE POLICY "User views personal notifications feed" ON public.notifications FOR SELECT USING (recipient_id = auth.uid());
CREATE POLICY "User updates personal notifications state" ON public.notifications FOR UPDATE USING (recipient_id = auth.uid());
CREATE POLICY "Admins full override access Notifications" ON public.notifications FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- N. Reviews RLS POLICIES
CREATE POLICY "Authenticated users read reviews listings" ON public.reviews FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "Customers submit reviews" ON public.reviews FOR INSERT TO authenticated WITH CHECK (customer_id = auth.uid());
CREATE POLICY "Admins full override access Reviews" ON public.reviews FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- O. Disputes RLS POLICIES
CREATE POLICY "Submitting client view disputes logs" ON public.disputes FOR SELECT USING (raised_by = auth.uid());
CREATE POLICY "Submitting client submit dispute" ON public.disputes FOR INSERT TO authenticated WITH CHECK (raised_by = auth.uid());
CREATE POLICY "Admins full override access Disputes" ON public.disputes FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- P. Payments RLS POLICIES
CREATE POLICY "Client view payments invoices" ON public.payments FOR SELECT TO authenticated USING (
    EXISTS (
        SELECT 1 FROM public.bookings b 
        WHERE b.id = booking_id AND (b.customer_id = auth.uid() OR b.technician_id = auth.uid())
    )
);
CREATE POLICY "Admins full override access Payments" ON public.payments FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- Q. Payouts RLS POLICIES
CREATE POLICY "Technician view personal payout invoice" ON public.payouts FOR SELECT USING (technician_id = auth.uid());
CREATE POLICY "Technician request payouts" ON public.payouts FOR INSERT TO authenticated WITH CHECK (technician_id = auth.uid());
CREATE POLICY "Admins full override access Payouts" ON public.payouts FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- R. Earnings RLS POLICIES
CREATE POLICY "Technician view personal database earnings" ON public.earnings FOR SELECT USING (technician_id = auth.uid());
CREATE POLICY "Admins full override access Earnings" ON public.earnings FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- S. Service Categories/Services Catalogues
CREATE POLICY "General Public read classifications" ON public.service_categories FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "Admins full override access Categories" ON public.service_categories FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

CREATE POLICY "General Public read services catalog" ON public.services FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "Admins full override access Services" ON public.services FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- T. Technician Services mappings
CREATE POLICY "Listing expertise read mappings" ON public.technician_services FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "Technicians link category skills" ON public.technician_services FOR ALL TO authenticated USING (technician_id = auth.uid()) WITH CHECK (technician_id = auth.uid());
CREATE POLICY "Admins full override access Tech Services Map" ON public.technician_services FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- U. Admin settings RLS policies
CREATE POLICY "Listing system operational configurations" ON public.admin_settings FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "Admins full override access System Settings" ON public.admin_settings FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- V. Audit logs RLS policies
CREATE POLICY "Admins full audit review reports" ON public.audit_logs FOR ALL TO authenticated USING (public.get_auth_role() = 'admin');

-- ----------------------------------------------------
-- 8. DATABASE VIEWS
-- ----------------------------------------------------

-- booking_activity_view: Analytics view for system status monitor dashboard
CREATE OR REPLACE VIEW public.booking_activity_view AS
SELECT 
    b.id AS booking_id,
    b.service_category,
    b.service_name,
    b.price,
    b.status,
    b.created_at,
    c.name AS customer_name,
    c.city AS customer_city,
    t.name AS technician_name,
    t.phone AS technician_phone
FROM 
    public.bookings b
JOIN 
    public.customers c ON b.customer_id = c.id
LEFT JOIN 
    public.technicians t ON b.technician_id = t.id;

-- ====================================================
-- 9. ENABLE REALTIME REPLICATION FOR INSTANT DISPATCH
-- ====================================================
DO $$
BEGIN
  -- Set replica identity to full to propagate previous values on update
  ALTER TABLE public.bookings REPLICA IDENTITY FULL;
  ALTER TABLE public.technicians REPLICA IDENTITY FULL;
  
  -- Add tables to the supabase_realtime publication
  ALTER PUBLICATION supabase_realtime ADD TABLE public.bookings;
  ALTER PUBLICATION supabase_realtime ADD TABLE public.technicians;
EXCEPTION
  WHEN duplicate_object THEN
    -- If already in publication, safe to ignore
    NULL;
  WHEN OTHERS THEN
    -- Ignore other issues (e.g. publication doesn't exist yet on local dev setups)
    NULL;
END;
$$;
